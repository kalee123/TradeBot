# TradeBot — Architecture

Target architecture for an automated trading system on **Zerodha Kite Connect v3**, built on
Spring Boot 3.3 / Java 17.

This document has two halves:

- **Part A — Current state**: what exists today and what is broken.
- **Part B — Target architecture**: the design to build toward, in phases.

Companion doc: [kite-auth-flow.md](kite-auth-flow.md) (auth sequence, DB columns, error table).

---

# Part A — Current State Review

## A.1 What exists

| Layer | Component | Status |
|---|---|---|
| Entry | `AuthController` — `/login`, `/kite/callback` | wired, but broken (A.2) |
| Security | `SecurityConfig`, `SessionAuthFilter` | wired, but never authenticates anyone |
| Broker auth | `KiteAuthService` — checksum + `POST /session/token` | correct |
| Broker HTTP | `BrokerApiClient` — `GET` with `Authorization: token key:token` | correct, but no timeouts |
| Persistence | `UserEntity`, `AuthTokenEntity`, 2 repositories | entities are stubs (A.2) |
| Config | `KiteProperties` bound to `kite.*` | correct |
| Util | `utilityHelper`, `KiteSession`, constants | `KiteSession` unused |

Nothing exists yet for market data, strategies, orders, positions, risk, or scheduling.
The `kiteconnect:3.5.0` dependency is declared but never used — all Kite calls are hand-rolled
`HttpURLConnection`.

## A.2 Blocking defects

These are ordered by severity. Items 1–5 mean the app cannot complete a login today.

**1. Live API credentials are committed to git.** — [application.yml:2-3](../src/main/resources/application.yml#L2-L3)

`api-key` and `api-secret` are in plaintext and have been tracked since commit `83b9263`. Removing
them from the working tree does not remove them from history.

Required actions, in order:
1. Rotate the API secret in the Kite developer console **now** — treat the current one as public.
2. Move config to environment variables (see [B.8](#b8-configuration--secrets)).
3. Purge from history (`git filter-repo`) or, if the repo has never been pushed anywhere, accept
   the history and rely on rotation.

**2. `AuthTokenEntity` setters are empty method bodies.** — [AuthTokenEntity.java:36-44](../src/main/java/com/app/tradebot/entity/AuthTokenEntity.java#L36-L44)

`setAccessToken`, `setApiKey`, and `setExpiresAt` have no bodies. Every call is a silent no-op,
so the token object is always empty. There are no getters at all.

**3. `UserEntity.addAuthToken()` is an empty method and there is no relationship mapping.** — [UserEntity.java:100](../src/main/java/com/app/tradebot/entity/UserEntity.java#L100)

`UserEntity` has no `@OneToMany` to `AuthTokenEntity`, so nothing cascades. Combined with #2, the
access token is **never persisted anywhere** — it is fetched and discarded.

**4. A successful login returns HTTP 500 and leaks the raw access token.** — [AuthController.java:40-43](../src/main/java/com/app/tradebot/controller/AuthController.java#L40-L43)

`userAuthService.authenticateUser()` returns the **access token** ([UserAuthService.java:41](../src/main/java/com/app/tradebot/authentication/UserAuthService.java#L41)),
but the controller compares it to the string `"success"`. That comparison can never be true, so the
success path is dead code and every login falls into:

```java
return ResponseEntity.status(500).body("Authentication failed: " + response);
```

which writes the Zerodha access token into an HTTP response body sent to the browser. Two defects
in one line: the flow never succeeds, and it exposes the credential that authorises trades on the
user's account.

**5. `USER_AUTH` is read but never written.** — [SessionAuthFilter.java:23](../src/main/java/com/app/tradebot/authentication/SessionAuthFilter.java#L23)

`FieldConstants.FIELD_USER_AUTH` has exactly one usage — the read in the filter. No code ever sets
it. So even if #4 were fixed, every request after callback returns `401 Not logged in`.

**6. The CSRF `state` check is bypassable.** — [AuthController.java:35](../src/main/java/com/app/tradebot/controller/AuthController.java#L35)

```java
if (state != null && saved != null && !saved.equals(state)) { ... reject ... }
```

If the attacker omits `state` from the callback URL, `state == null` short-circuits the condition
and the request is accepted. Invert it: reject unless `saved != null && saved.equals(state)`, and
clear the session attribute after a single use so a valid state cannot be replayed.

**7. Every login inserts a duplicate user.** — [UserAuthService.java:59](../src/main/java/com/app/tradebot/authentication/UserAuthService.java#L59)

`getUserInfo` unconditionally does `new UserEntity()` + `save()`. `UserRepository` already has
`findByUserBrokerId` — this should be an upsert on the Kite `user_id`. As written, the second login
by the same user violates the `user_broker_id` unique constraint.

**8. Two competing authentication mechanisms.** — [SecurityConfig.java](../src/main/java/com/app/tradebot/authentication/SecurityConfig.java), [SessionAuthFilter.java](../src/main/java/com/app/tradebot/authentication/SessionAuthFilter.java)

`SessionAuthFilter` writes its own `401` and never populates the `SecurityContext`; the chain then
also declares `.anyRequest().authenticated()`. Two independent gates, neither aware of the other.
Pick one — see [B.3](#b3-authentication--session).

## A.3 Secondary issues

| Issue | Location | Fix |
|---|---|---|
| `hashCode()` returns constant `0` | [UserEntity.java:96](../src/main/java/com/app/tradebot/entity/UserEntity.java#L96) | Return `getClass().hashCode()` — stable across the JPA lifecycle, unlike hashing a generated id |
| Hardcoded `"YOUR_API_KEY"` | [UserAuthService.java:67](../src/main/java/com/app/tradebot/authentication/UserAuthService.java#L67) | Inject `KiteProperties` |
| Expiry hardcoded to `+12h` | [UserAuthService.java:68](../src/main/java/com/app/tradebot/authentication/UserAuthService.java#L68) | Kite tokens die at a fixed wall-clock time each morning, not 12h after issue — see [B.3](#b3-authentication--session) |
| `schema = "tradedb"` on `AuthTokenEntity` only | [AuthTokenEntity.java:7](../src/main/java/com/app/tradebot/entity/AuthTokenEntity.java#L7) | Drop it; set the schema once via `spring.jpa.properties.hibernate.default_schema` |
| No connect/read timeouts | [KiteApiClient.java](../src/main/java/com/app/tradebot/config/KiteApiClient.java) | A hung Kite socket blocks a request thread indefinitely. Replace with `RestClient` (B.4) |
| `@Scope("request")` on a controller | [AuthController.java:17](../src/main/java/com/app/tradebot/controller/AuthController.java#L17) | Meaningless on a stateless `@RestController`; remove |
| `@Lazy` on repository injection | [UserAuthService.java:26-32](../src/main/java/com/app/tradebot/authentication/UserAuthService.java#L26-L32) | Papers over a wiring problem rather than fixing it; remove and use constructor injection |
| Field injection throughout | `UserAuthService`, `AuthController` | Constructor injection — makes dependencies explicit and testable |
| `org.json` used but not declared | `KiteAuthService`, `UserAuthService`, `utilityHelper` | Arrives transitively via `kiteconnect`. Either declare it, or standardise on Jackson (already on the classpath via `spring-boot-starter-web`) |
| `ddl-auto: update` | [application.yml](../src/main/resources/application.yml) | Never for a system holding trade records. Flyway + `validate` (B.7) |
| `utilityHelper` lowercase class name | [utils/](../src/main/java/com/app/tradebot/utils/) | `JsonUtils`; make the class `final` with a private constructor |
| No tests beyond `contextLoads` | `src/test/` | See [B.11](#b11-testing) |
| `csrf().disable()` globally | [SecurityConfig.java:24](../src/main/java/com/app/tradebot/authentication/SecurityConfig.java#L24) | Acceptable for a token-authenticated JSON API; **not** acceptable while auth is cookie/session based |

---

# Part B — Target Architecture

## B.1 Design principles

1. **The broker is an external system, not a library call.** Every Kite interaction goes through one
   adapter package. Strategy and risk code must never import `com.zerodhatech.*`. This is what makes
   backtesting and a second broker possible later.
2. **Never trust the loop with money.** Every order passes a risk gate that can veto it, and the gate
   is not bypassable by strategy code.
3. **The database is the source of truth for intent; the broker is the source of truth for reality.**
   Reconcile the two continuously; on conflict, the broker wins.
4. **Fail closed.** Lost connectivity, expired token, unknown state → stop trading, alert, do not guess.
5. **Every decision is reconstructible.** A trade you cannot explain three weeks later is a trade you
   cannot debug or defend.

## B.2 Package layout

```
com.app.tradebot
├── TradeBotApplication
├── config/               Spring @Configuration, @ConfigurationProperties, RestClient beans
├── broker/               ── Kite adapter (the ONLY package importing Kite types) ──
│   ├── auth/               KiteAuthService, KiteSessionManager, token store
│   ├── rest/               KiteRestClient — orders, positions, holdings, margins, quotes
│   ├── ticker/             KiteTickerClient — WebSocket, reconnect, subscription set
│   ├── instrument/         Daily instrument-dump loader, symbol ↔ token registry
│   └── dto/                Kite wire DTOs, mapped to domain types at the boundary
├── marketdata/           Tick ingest → bar aggregation → indicator series
│   ├── TickBus             In-process fan-out of ticks to subscribers
│   ├── BarAggregator       Tick → 1m/5m/15m OHLCV candles
│   └── indicator/          EMA, RSI, ATR, VWAP … (incremental, not recomputed per bar)
├── strategy/             ── Pure decision logic. No I/O, no Spring, no Kite ──
│   ├── Strategy            Interface: onBar(BarEvent) → List<Signal>
│   ├── StrategyRegistry    Discovery + per-strategy enable/disable
│   └── impl/               Concrete strategies
├── risk/                 ── The veto gate. Mandatory, not optional ──
│   ├── RiskEngine          Signal → Approved | Rejected(reason)
│   ├── PositionSizer       Signal + capital + stop distance → quantity
│   └── rule/               Per-trade loss cap, daily loss cap, exposure, max positions, kill switch
├── execution/            Approved order → broker → tracked to terminal state
│   ├── OrderService        Place / modify / cancel, idempotent
│   ├── OrderStateMachine   PENDING → OPEN → COMPLETE | REJECTED | CANCELLED
│   └── ExecutionListener   Consumes order updates from the ticker
├── portfolio/            Live positions, realised/unrealised P&L, reconciliation vs broker
├── persistence/
│   ├── entity/
│   └── repository/
├── scheduler/            Pre-open, square-off, EOD reconcile, instrument refresh
├── api/                  REST controllers + DTOs (bot control, status, history)
├── security/             App-level auth, distinct from broker auth
└── common/               Errors, clock, JSON, IST time helpers
```

The dependency rule: `strategy` and `risk` depend on nothing but `common` and domain types.
Everything else may depend on them. Not the reverse.

## B.3 Authentication & session

Two *separate* concerns that the current code conflates:

| | Broker session | App session |
|---|---|---|
| Identity | Zerodha `user_id` | Your `users.user_id` |
| Credential | Kite `access_token` | Your JWT / session cookie |
| Lifetime | Expires each morning | You choose (e.g. 24h + refresh) |
| Visible to browser | **Never** | Yes |
| Stored | Encrypted, DB | Client-side |

**Flow** (fixes A.2 #4–#7):

```
GET /login
  → generate state (UUID), store in session
  → 302 to kite.zerodha.com/connect/login

GET /kite/callback?request_token=R&state=S
  → require saved != null && saved.equals(S), then invalidate the stored state
  → POST /session/token  → access_token + kite user_id
  → GET /user/profile
  → upsert user BY user_broker_id       (not blind insert)
  → deactivate prior active tokens, insert new one (encrypted)
  → establish the app session
  → 302 to /dashboard        (no token in the body, ever)
```

**Token lifetime.** A Kite access token is not valid for a rolling 12 hours — it is invalidated at a
fixed time each morning IST. Do not hardcode a duration. Compute the next expiry boundary in
`Asia/Kolkata` and store that as `expires_at`, and treat the broker's `TokenException` as the real
authority: on that error, mark the token inactive, halt trading, and require re-login. Confirm the
exact cutoff against current Kite docs before you rely on it.

**Daily re-login is unavoidable.** Kite has no unattended refresh for non-subscription accounts. The
bot cannot start trading each morning without a human completing the login. Design for this:

- Startup enters `AWAITING_AUTH` and takes no positions.
- A pre-open scheduled job checks for a valid token and alerts if absent.
- Overnight positions are still monitored on the next session's token, not blindly held.

**Encryption at rest.** `access_token` must be encrypted in the DB — a JPA `AttributeConverter` with
AES-GCM and a key from the environment. A leaked DB dump otherwise equals full trading access.

**App-level security.** Delete `SessionAuthFilter` and let Spring Security own authentication: a
single filter that validates the app credential and populates the `SecurityContext`, with
`.requestMatchers("/login", "/kite/callback").permitAll()` and everything else authenticated. One
gate, one source of truth.

## B.4 Broker adapter

**HTTP.** Replace `HttpURLConnection` with a configured `RestClient` bean:

- Connect timeout ~3s, read timeout ~10s. Never unbounded.
- `X-Kite-Version: 3` and the auth header set once, in the bean.
- Jackson deserialisation into DTOs, not `JSONObject` field-picking.
- A single retry policy: retry idempotent GETs on 5xx/timeout with backoff; **never** auto-retry
  order placement — a timeout does not mean the order was rejected. Reconcile instead (B.6).

**Rate limits.** Kite enforces per-endpoint-category limits (roughly: quotes ~1/s, historical ~3/s,
orders ~10/s, other ~10/s, plus daily order caps). Verify the current numbers in the Kite docs —
they change. Enforce them yourself with a per-category rate limiter in the adapter, so a strategy bug
cannot get the API key throttled or banned. A breach is an account-level problem, not a request-level one.

**Should you use the `kiteconnect` SDK?** You have the dependency and aren't using it. Decide
deliberately:

- **Use it** for the WebSocket ticker — the binary tick protocol is fiddly and not worth
  reimplementing.
- **Hand-roll or use it** for REST, your call; the endpoints are simple. What matters is that either
  choice stays behind your own interface so the rest of the app never sees Kite types.

## B.5 Market data

```
KiteTicker (WebSocket)
   │  ticks (up to ~3000 instruments/connection, max ~3 connections per api_key — verify)
   ▼
TickBus ──────────────► BarAggregator ──────► IndicatorEngine ──────► Strategy.onBar()
   │                    (1m/5m/15m OHLCV)     (incremental EMA/RSI…)
   └──────────────────► PortfolioService (mark-to-market)
```

- **Instrument registry.** Kite identifies instruments by numeric token, not symbol. Download the
  instrument dump **once per day** (it is several MB) and cache the symbol ↔ token mapping. Never
  fetch it per request.
- **Bar close is the trigger, not every tick.** Firing strategies on raw ticks produces overtrading
  and unrepeatable behaviour. Emit a `BarClosed` event on the boundary and drive decisions from that.
- **Incremental indicators.** Keep rolling state per instrument; recomputing a 200-period EMA over a
  full history on every bar will not scale past a handful of symbols.
- **Reconnection is normal, not exceptional.** The socket will drop. On reconnect: resubscribe the
  full set, mark the gap, and have strategies treat a gapped series as untradeable until it refills.

## B.6 Execution & order lifecycle

```
Signal ──► RiskEngine ──► PositionSizer ──► OrderService.place()
                │                                   │
             Rejected                          broker order_id
             (logged,                               │
              never silent)         ┌───────────────┴───────────────┐
                                    ▼                               ▼
                            WebSocket order updates          Polling fallback
                                    └───────────────┬───────────────┘
                                                    ▼
                                          OrderStateMachine
                                   PENDING → OPEN → COMPLETE
                                              ↘ REJECTED / CANCELLED
```

**Idempotency.** Generate a client-side `clientOrderId` (UUID) per intent, persist it *before*
calling the broker, and store the broker `order_id` on return. If the call times out, you have a
durable record to reconcile against — this is the difference between "did my order go through?" and
a duplicate position.

**Order updates arrive on the ticker connection.** Consume them as the primary signal; poll
`/orders` as a slower fallback. Do not rely on polling alone — you will miss intermediate states.

**Reconciliation loop.** On a fixed interval and at EOD, fetch positions and orders from Kite and
diff against local state. Any drift is an incident: log it, alert, and trust the broker's view.

**Square-off.** Intraday (MIS) positions are force-closed by the broker near market close at an
unfavourable price. Run your own square-off job several minutes earlier, on your terms.

## B.7 Data model

Extends the schema in [kite-auth-flow.md](kite-auth-flow.md#6-database-schema-recap).

| Table | Purpose | Key columns |
|---|---|---|
| `users` | exists | `user_broker_id` unique |
| `auth_tokens` | exists | `access_token` **encrypted**, `is_active`, `expires_at` |
| `instruments` | daily dump cache | `instrument_token` PK, `tradingsymbol`, `exchange`, `lot_size`, `tick_size`, `expiry` |
| `strategies` | config + enable flag | `name`, `enabled`, `params` (jsonb), `max_capital` |
| `signals` | every decision, incl. rejected | `strategy_id`, `instrument_token`, `side`, `generated_at`, `bar_time`, `inputs` (jsonb) |
| `orders` | intent + broker state | `client_order_id` unique, `broker_order_id`, `status`, `qty`, `filled_qty`, `avg_price`, `product`, `order_type` |
| `trades` | fills | `order_id`, `price`, `qty`, `exchange_ts` |
| `positions` | current + historical | `instrument_token`, `qty`, `avg_price`, `realised_pnl`, `unrealised_pnl` |
| `risk_events` | breaches and vetoes | `rule`, `severity`, `payload`, `occurred_at` |
| `bars` | OHLCV cache | `(instrument_token, interval, ts)` composite PK |

Notes:
- **Money is `NUMERIC(18,4)`, never `double`.** Floating-point P&L will not reconcile.
- **All timestamps `TIMESTAMPTZ`, stored UTC, presented in `Asia/Kolkata`.** Market sessions,
  expiries, and square-off times are all IST-bound; a naive `LocalDateTime` will bite during
  analysis.
- `signals` records rejected decisions too. "Why didn't the bot trade?" is asked more often than
  "why did it?"
- Adopt **Flyway** now, before there is data to migrate. Switch `ddl-auto` to `validate`.

## B.8 Configuration & secrets

```yaml
kite:
  api-key:    ${KITE_API_KEY}
  api-secret: ${KITE_API_SECRET}
  redirect-url: ${KITE_REDIRECT_URL}
app:
  encryption-key: ${APP_ENCRYPTION_KEY}     # AES key for token-at-rest
trading:
  mode: PAPER                               # PAPER | LIVE
  kill-switch: false
  max-daily-loss: 5000
  max-positions: 5
  square-off-time: "15:15"
```

- No secret in any tracked file. `application-local.yml` in `.gitignore` for dev.
- Profiles: `local` (H2, paper) / `prod` (Postgres, live).
- **`mode: PAPER` is the default.** Going live must be an explicit, deliberate config change.
- **Kill switch**: a runtime flag the risk engine checks before every order. One toggle stops all
  new entries without a redeploy.

## B.9 Concurrency & scheduling

| Concern | Approach |
|---|---|
| Tick ingest | Single WebSocket thread → bounded queue. **Drop-oldest on overflow**, never block the socket |
| Bar/strategy work | Dedicated pool, partitioned by instrument so one symbol's state is single-threaded |
| Order placement | Serialised per instrument. Concurrent orders on one symbol cause double-entry |
| Scheduled jobs | `@Scheduled` with explicit `Asia/Kolkata` zone |

Scheduled jobs: instrument refresh (pre-open) · token validity check (pre-open) · reconciliation
(every few minutes during market hours) · square-off (`square-off-time`) · EOD P&L snapshot and
token deactivation.

Guard every job with a market-calendar check — holidays and special sessions are not weekends.

## B.10 Resilience, observability, and money-specific failure modes

**Bot state machine** — one explicit state, exposed over the API:

```
STARTING → AWAITING_AUTH → READY → RUNNING → HALTED
                                       ↘ SQUARING_OFF → FLAT
```

`HALTED` is entered automatically on: token expiry, WebSocket down beyond a threshold, daily loss cap
hit, reconciliation drift, or kill switch. Exit from `HALTED` is manual.

**Observability.** Spring Boot Actuator + Micrometer. Custom health indicators for
`kite-token`, `websocket`, and `broker-reachable`. Metrics for tick lag, order latency, fill rate,
rejection rate, open positions, daily P&L. **Structured logs with `clientOrderId` as the correlation
key** — that id should let you trace one decision from signal to fill.

**Alerting.** Any `HALTED` transition, any risk breach, any reconciliation drift, and failure to
authenticate before market open, all pushed to a channel a human actually reads. A bot that fails
silently at 09:14 is worse than one that never started.

**Failure modes specific to this domain:**

| Failure | Consequence | Mitigation |
|---|---|---|
| Order times out, actually filled | Duplicate position on retry | `clientOrderId` + reconcile, never blind retry |
| Token expires mid-session | All calls 403; positions unmanaged | Health check + `HALTED` + alert |
| WebSocket drops silently | Stale prices, decisions on old data | Heartbeat, staleness check on every bar |
| Strategy bug → order loop | Rate-limit ban, real losses | Rate limiter + max-orders-per-minute risk rule |
| Clock skew | Bars misaligned to exchange time | NTP; use exchange timestamps from ticks, not local time |
| Partial fill treated as full | Wrong position size, wrong stop | Track `filled_qty` separately; act on fills, not placements |

## B.11 Testing

| Level | Scope |
|---|---|
| Unit | Indicators against known series; risk rules at their boundaries; position sizing |
| Unit | Strategies — pure functions, fed a scripted bar sequence, no Spring context |
| Integration | Repositories on Testcontainers Postgres |
| Contract | Broker adapter against WireMock — including 403, 429, timeout, and malformed responses |
| Replay | Recorded tick sessions replayed through the full pipeline; assert on the order sequence |
| Manual | Paper mode against live market data for a sustained period before any real capital |

Non-negotiable: **`OrderService` must be tested against a mocked broker that times out and that
double-responds.** Those are the paths that cost money, and they are the paths never exercised in
happy-path testing.

## B.12 Build order

Each phase should be deployable and verifiable before the next begins.

**Phase 0 — Stop the bleeding.** Rotate the leaked secret. Move config to env vars. Fix the empty
setters, the `"success"` comparison, the token leak in the response body, the `state` check, and the
duplicate-user insert. Set the app session so login actually works. *Exit criterion: log in, and a
second login updates rather than duplicates the user.*

**Phase 1 — Foundation.** Flyway. Constructor injection. `RestClient` with timeouts. Token
encryption. Delete `SessionAuthFilter` in favour of one Spring Security chain. Actuator + health
indicators. *Exit criterion: `/actuator/health` accurately reports broker token state.*

**Phase 2 — Broker adapter.** Instrument dump loader. Read-only REST: profile, margins, holdings,
positions, quotes. Rate limiter. *Exit criterion: an API endpoint returns your real portfolio.*

**Phase 3 — Market data.** WebSocket ticker with reconnect. Tick bus, bar aggregator, two or three
indicators. *Exit criterion: 1-minute bars for a watchlist, matching the Kite chart.*

**Phase 4 — Strategy + risk, paper only.** `Strategy` interface, one simple strategy, the full risk
engine, signal persistence. Orders simulated, never sent. *Exit criterion: a day of paper signals
with an explainable log for every one, including rejections.*

**Phase 5 — Execution.** `OrderService`, state machine, order-update consumer, reconciliation,
square-off job. Still paper-gated. *Exit criterion: reconciliation runs clean for a week.*

**Phase 6 — Live, small.** Flip `mode: LIVE` with the tightest possible risk caps and a single
instrument. Scale only after the numbers reconcile against the Zerodha console daily.

---

## B.13 Regulatory note

SEBI has an evolving framework for retail algorithmic trading via broker APIs, including
registration of algos with exchanges above certain order-rate thresholds, and broker-side
obligations around API-originated orders. This affects what you are permitted to run and at what
rate. **Check the current SEBI circulars and Zerodha's own algo/API policy before running a live
automated strategy** — the rules changed recently and the details are outside what this document
can pin down reliably.

---

## Open decisions

| Decision | Options | Note |
|---|---|---|
| Single-user or multi-user | Single (simpler, one token) vs multi-tenant | Schema already supports multi-user; the ticker/rate-limit design differs substantially |
| Kite SDK vs hand-rolled REST | SDK / own client / hybrid | Recommend: SDK for the ticker, own client for REST, both behind your interface |
| Historical data source | Kite historical API (paid add-on) vs self-recorded bars | Needed for backtesting; affects Phase 4 |
| Backtesting | In-process replay vs separate harness | Replay through the real pipeline avoids logic drift between backtest and live |
| Deployment | Local / VPS / cloud | Must be in or near India for latency, and reliably up 09:00–15:30 IST |
