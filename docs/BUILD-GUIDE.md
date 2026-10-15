# TradeBot — Product & Build Guide

What the app *is*, screen by screen and feature by feature, and the domain rules you have to handle
to make it safe.

Companions:
- [ARCHITECTURE.md](ARCHITECTURE.md) — backend structure, packages, data model, build phases
- [kite-auth-flow.md](kite-auth-flow.md) — auth sequence detail

This doc is the **product** view. Where it says "Phase N", that maps to
[ARCHITECTURE.md §B.12](ARCHITECTURE.md#b12-build-order).

---

## 1. What you are building

A single-operator system that watches Indian equity/F&O markets through Zerodha, decides trades from
rules you configure, places them, manages them, and shows you exactly what it did and why.

**In scope**
- Automated intraday and positional strategies on NSE/BSE equity and NFO derivatives
- Paper mode and live mode, with paper as the default
- One dashboard that answers "what is it doing right now, and how much am I up or down"
- A complete audit trail — every signal, including the ones that were rejected

**Explicitly not in scope** (decide later, don't drift into them)
- Multi-user SaaS. The schema supports it; the UI, ticker budget, and rate limiting do not.
- Options greeks, IV surfaces, or strategy builders for multi-leg spreads
- Charting that competes with Kite. Link out to Kite charts instead.
- HFT or sub-second latency. You are on a REST/WebSocket broker API from a rented box.

**The one product rule that outranks all others:** the operator must always be able to answer
*"why did it do that?"* and *"how do I make it stop?"* — from any screen, in under five seconds.

---

## 2. The operator's day

The clearest way to see what the app must do. Times are IST.

| Time | What happens | What the app must do |
|---|---|---|
| 08:45 | Operator wakes up | Alert already sent: "Kite login required before 09:00" |
| 08:50 | Opens app | Dashboard shows `AWAITING_AUTH`, one big **Login with Kite** button |
| 08:51 | Completes Kite 2FA | Redirects back, state → `READY`. Instrument dump refreshed. Watchlist LTPs live |
| 08:55 | Reviews setup | Sees which strategies are enabled, capital allocated, yesterday's carry-forward positions |
| 09:07 | Pre-open | Banner: "Pre-open session — no orders placed until 09:15" |
| 09:15 | Market opens | State → `RUNNING`. Ticks flowing, bars forming, P&L ticking |
| 09:47 | First signal | Signal feed shows it live; if rejected, the rejecting rule is named |
| 09:47 | Order placed | Orders table row appears `PENDING` → `OPEN` → `COMPLETE`. Position card appears |
| 11:20 | Operator at work | Opens app on phone. Needs: P&L, open positions, kill switch. Nothing else |
| 12:30 | Bad day | Daily loss cap hit → state `HALTED`, alert fires, no new entries, existing positions still managed |
| 12:31 | Operator decides | Either exits manually, or clears the halt deliberately |
| 15:15 | Square-off | Bot closes intraday positions on its own schedule, before the broker's forced close |
| 15:30 | Close | State → `FLAT`. EOD reconcile against Kite. Day's P&L snapshotted |
| 21:00 | Review | Reads the trade journal: every signal, fill, and rejection, with the inputs behind each |

If a feature does not serve one of these moments, it is Phase 6+.

---

## 3. Feature inventory

`M` must-have · `S` should-have · `C` could-have

| # | Feature | Pri | Phase | Notes |
|---|---|---|---|---|
| **Auth & session** |
| 1.1 | Kite login + callback | M | 0 | See [ARCHITECTURE §B.3](ARCHITECTURE.md#b3-authentication--session) |
| 1.2 | App session separate from broker token | M | 0 | Kite token never reaches the browser |
| 1.3 | Token expiry detection + re-login prompt | M | 1 | Daily, unavoidable |
| 1.4 | Pre-open "login required" alert | S | 5 | Push/email/Telegram |
| **Bot control** |
| 2.1 | Bot state machine, visible everywhere | M | 1 | `AWAITING_AUTH / READY / RUNNING / HALTED / SQUARING_OFF / FLAT` |
| 2.2 | Kill switch | M | 4 | Stops new entries instantly, no redeploy |
| 2.3 | Paper ↔ Live toggle | M | 4 | Typed confirmation to go live |
| 2.4 | Square-off all, manual | M | 5 | Confirmation modal, shows what will close |
| 2.5 | Clear halt | M | 4 | Manual only, never automatic |
| **Market data** |
| 3.1 | Watchlist with live LTP + %chg | M | 3 | |
| 3.2 | Instrument search (symbol → token) | M | 2 | From the daily dump |
| 3.3 | Live candle/indicator preview | C | 6 | Nice, not necessary — Kite has charts |
| 3.4 | Tick staleness indicator | M | 3 | "Last tick 47s ago" is critical information |
| **Strategies** |
| 4.1 | Strategy list, enable/disable | M | 4 | |
| 4.2 | Per-strategy parameter config | M | 4 | Typed, validated, versioned |
| 4.3 | Assign instruments to a strategy | M | 4 | |
| 4.4 | Per-strategy capital allocation | M | 4 | |
| 4.5 | Per-strategy P&L attribution | S | 5 | Which strategy is actually working |
| 4.6 | Backtest a strategy over history | S | 6 | Needs historical data (paid Kite add-on) |
| **Risk** |
| 5.1 | Daily loss cap → auto-halt | M | 4 | The single most important feature in the app |
| 5.2 | Per-trade risk % / position sizing | M | 4 | |
| 5.3 | Max concurrent positions | M | 4 | |
| 5.4 | Max exposure (absolute + % of capital) | M | 4 | |
| 5.5 | Max orders per minute | M | 4 | Protects against a runaway loop |
| 5.6 | Risk event log | M | 4 | Every veto, with the rule that fired |
| **Execution** |
| 6.1 | Live orders table with state | M | 5 | |
| 6.2 | Manual cancel / modify | M | 5 | |
| 6.3 | Open positions + unrealised P&L | M | 5 | |
| 6.4 | Manual exit of a single position | M | 5 | |
| 6.5 | Reconciliation status vs Kite | M | 5 | Drift is an incident, surface it |
| 6.6 | Scheduled square-off | M | 5 | |
| **Visibility** |
| 7.1 | Signal feed, incl. rejections + reason | M | 4 | |
| 7.2 | Trade journal / history | M | 5 | Filter by date, strategy, symbol |
| 7.3 | Daily P&L chart + stats | S | 5 | Win rate, avg win/loss, max drawdown |
| 7.4 | CSV export | S | 6 | For tax and your own analysis |
| 7.5 | System log viewer | S | 5 | Correlated by `clientOrderId` |
| **Ops** |
| 8.1 | Health panel (token / socket / broker / DB) | M | 1 | |
| 8.2 | Alerts to Telegram or email | S | 5 | Halt, risk breach, auth needed, drift |
| 8.3 | Market calendar awareness | M | 3 | Holidays are not weekends |
| 8.4 | Settings (caps, times, notifications) | M | 4 | |

---

## 4. Screen map

```
                        ┌──────────────┐
                        │    Login     │   unauthenticated only
                        └──────┬───────┘
                               ▼
   ┌─────────────────── App shell ────────────────────┐
   │  header: bot state · P&L today · mode · kill     │
   │  ┌────────────────────────────────────────────┐  │
   │  │  Dashboard      ← default                  │  │
   │  │  Strategies  → Strategy detail             │  │
   │  │  Watchlist                                 │  │
   │  │  Signals                                   │  │
   │  │  Orders                                    │  │
   │  │  Positions                                 │  │
   │  │  Journal     → Trade detail                │  │
   │  │  Backtest    (phase 6)                     │  │
   │  │  Settings    → Risk / Alerts / Account     │  │
   │  └────────────────────────────────────────────┘  │
   └──────────────────────────────────────────────────┘
```

Mobile shows only: Dashboard, Positions, Orders, kill switch. Nothing else — you are not configuring
strategies on a phone at a bus stop.

---

## 5. Screen specifications

Each screen below lists its **wireframe**, the **states** it must handle, and the **endpoints** behind it.

Every screen must handle five states. Design them all — not just the happy one.
`loading` · `empty` · `error` · `market closed` · `bot halted`

---

### 5.1 App shell (persistent header)

Present on every screen. This is the "answer in five seconds" surface.

```
┌────────────────────────────────────────────────────────────────────────────────┐
│ TradeBot   ● RUNNING    P&L today  +₹4,280 (+1.2%)   [PAPER]      ⛔ KILL      │
│            ○ socket ok · token ok · reconciled 12:44:02                        │
└────────────────────────────────────────────────────────────────────────────────┘
```

- **State dot colour**: green `RUNNING` · blue `READY` · amber `SQUARING_OFF` · red `HALTED` ·
  grey `FLAT` / `AWAITING_AUTH`
- **`[PAPER]` / `[LIVE]` badge is loud.** Live mode gets a persistent coloured border on the whole
  app. Nobody should ever be unsure which mode they're in.
- **Kill switch is always reachable**, always one confirmation click, never buried in settings.
- **Health strip** — socket, token, last reconcile. When any degrades, the strip turns amber and
  says what's wrong in words, not an icon.

`GET /api/bot/state` (polled or pushed) · `POST /api/bot/kill`

---

### 5.2 Login

```
┌────────────────────────────────────────────┐
│                                            │
│              TradeBot                      │
│                                            │
│   Your Kite session expires each morning.  │
│   Log in before 09:00 IST to trade today.  │
│                                            │
│       ┌──────────────────────────┐         │
│       │   Log in with Zerodha    │         │
│       └──────────────────────────┘         │
│                                            │
│   Last session: 17 Aug 2026, 08:52         │
└────────────────────────────────────────────┘
```

**Handle:** callback failure (`state` mismatch, expired `request_token`, Kite down) with a plain
message and a retry button. Never show a stack trace. Never put a token in the URL.

`GET /login` → 302 · `GET /kite/callback`

---

### 5.3 Dashboard

The one screen the operator stares at all day.

```
┌─ Today ────────────────┬─ Capital ──────────────┬─ Activity ────────────────┐
│ Realised    +₹3,100    │ Available   ₹1,84,200  │ Signals        14         │
│ Unrealised  +₹1,180    │ Deployed      ₹62,400  │ Orders          6         │
│ Net         +₹4,280    │ Exposure  34% of cap   │ Rejected        8         │
│ vs cap    -₹5,000 ▓░░░ │                        │ Fill rate     100%        │
└────────────────────────┴────────────────────────┴───────────────────────────┘

┌─ Open positions (3) ───────────────────────────────────────────────────────┐
│ SYMBOL      QTY   AVG      LTP      P&L        STRATEGY       ACTION        │
│ RELIANCE     50  2,842   2,868   +₹1,300 ▲     ema-cross      [Exit]        │
│ INFY        -75  1,510   1,504     +₹450 ▲     mean-revert    [Exit]        │
│ TATAMOTORS   40    978     964     -₹570 ▼     ema-cross      [Exit]        │
└────────────────────────────────────────────────────────────────────────────┘

┌─ Live signal feed ─────────────────────────┬─ Health ──────────────────────┐
│ 12:44  ✗ HDFCBANK  BUY  · daily loss cap   │ Kite token   ok, exp 06:00    │
│ 12:31  ✓ RELIANCE  BUY  50 @ 2,842 · filled│ WebSocket    ok, 42 instr     │
│ 12:18  ✗ INFY      SELL · max positions    │ Last tick    0.4s ago         │
│ 11:52  ✓ INFY      SELL 75 @ 1,510 · filled│ Reconcile    clean 12:44      │
└────────────────────────────────────────────┴───────────────────────────────┘
```

**Handle:**
- Daily-loss progress bar turns red **before** the cap, not at it
- Market closed → cards freeze with a "closed, showing final values" note rather than blanking
- `HALTED` → a banner across the top naming the rule that halted it and offering **Clear halt**
- Stale ticks (> 10s during market hours) → LTP and unrealised P&L greyed out with a staleness note.
  Showing a confidently wrong P&L is worse than showing none.

`GET /api/dashboard/summary` · `GET /api/positions` · `GET /api/signals?limit=20` · `WS /ws/stream`

---

### 5.4 Strategies

```
┌─ Strategies ────────────────────────────────────────── [+ New strategy] ──┐
│                                                                           │
│ ● ema-cross            RUNNING    3 instruments   ₹50,000   +₹2,840  ⚙   │
│ ● mean-revert          RUNNING    2 instruments   ₹40,000   +₹1,440  ⚙   │
│ ○ breakout-orb         DISABLED   5 instruments   ₹60,000        —   ⚙   │
│ ⚠ gap-fade             ERROR      1 instrument         —         —   ⚙   │
│    └ last error 09:31 — indicator window not filled                       │
└───────────────────────────────────────────────────────────────────────────┘
```

**Strategy detail**

```
┌─ ema-cross ───────────────────────────────── [Disable]  [Save changes] ───┐
│                                                                           │
│ Parameters                          Instruments                           │
│  Fast EMA      [   9 ]               ☑ RELIANCE      NSE                  │
│  Slow EMA      [  21 ]               ☑ INFY          NSE                  │
│  Timeframe     [ 5m  ▾]              ☑ TATAMOTORS    NSE                  │
│  Stop loss %   [ 1.5 ]               ☐ HDFCBANK      NSE                  │
│  Target %      [ 3.0 ]               [+ add instrument]                   │
│  Product       [ MIS ▾]                                                   │
│                                     Capital                               │
│ Entry window                         Allocated  [ ₹50,000 ]               │
│  From [09:20]  To [14:45]            Max per trade  [ ₹15,000 ]           │
│                                                                           │
│ ── Performance (30d) ──────────────────────────────────────────────────── │
│  Trades 42 · Win rate 57% · Avg win ₹840 · Avg loss ₹520 · Max DD ₹3,200  │
└───────────────────────────────────────────────────────────────────────────┘
```

**Handle:**
- **Parameter changes mid-session.** Either block edits while the strategy holds a position, or
  version the params and record which version produced each signal. Do not silently swap them.
- Validate ranges server-side (`fast < slow`, capital ≤ available, entry window inside market hours)
- Disabling a strategy with open positions → ask explicitly: close them, or leave them to be managed?
- An errored strategy must never take the whole bot down. Isolate it, mark it, keep the rest running.

`GET/POST/PUT /api/strategies` · `POST /api/strategies/{id}/enable|disable` · `GET /api/strategies/{id}/performance`

---

### 5.5 Watchlist

```
┌─ Watchlist ─────────────────────────── [search symbol…] ──────────────────┐
│ SYMBOL       LTP      CHG      %CHG    VOLUME     STRATEGIES     TICK      │
│ RELIANCE   2,868.4  +26.4    +0.93%    4.2M       ema-cross      0.3s      │
│ INFY       1,504.0   -6.0    -0.40%    2.8M       mean-revert    0.4s      │
│ NIFTY24AUG… 24,812   +112    +0.45%    —          —              0.3s      │
└───────────────────────────────────────────────────────────────────────────┘
```

**Handle:** search comes from the local instrument dump, not a live API call · a symbol with no ticks
shows "no data" rather than a stale price · F&O rows show expiry and lot size · warn when adding
beyond the ticker's instrument budget.

`GET /api/instruments/search?q=` · `GET/POST/DELETE /api/watchlist` · ticks over `WS /ws/stream`

---

### 5.6 Signals

The "why did it do that?" screen. Rejected signals matter as much as accepted ones.

```
┌─ Signals ── [Today ▾] [All strategies ▾] [✓ accepted ✓ rejected] ─────────┐
│ TIME   STRATEGY     SYMBOL     SIDE  OUTCOME                              │
│ 12:44  ema-cross    HDFCBANK   BUY   ✗ REJECTED — daily loss cap (-₹4,900 │
│                                        of -₹5,000)                    [›] │
│ 12:31  ema-cross    RELIANCE   BUY   ✓ ORDER a3f2 · filled 50 @ 2,842 [›] │
│ 12:18  mean-revert  INFY       SELL  ✗ REJECTED — max positions (3/3)  [›] │
└───────────────────────────────────────────────────────────────────────────┘
```

**Signal detail** must show the inputs the decision was made from — bar timestamp, OHLCV, indicator
values, the rule that triggered, the intended size and stop, and either the rejecting rule or the
resulting order id. Store this as a JSON blob at decision time; reconstructing it later is impossible.

`GET /api/signals` · `GET /api/signals/{id}`

---

### 5.7 Orders

```
┌─ Orders ── [Today ▾] ──────────────────────────────────────────────────────┐
│ TIME   SYMBOL     SIDE TYPE   QTY  FILLED  PRICE    STATUS     ACTION       │
│ 12:31  RELIANCE   BUY  LIMIT   50    50    2,842    COMPLETE   —            │
│ 12:29  TATAMOTORS BUY  MARKET  40    40      978    COMPLETE   —            │
│ 11:52  INFY       SELL LIMIT   75    75    1,510    COMPLETE   —            │
│ 11:40  SBIN       BUY  LIMIT  100     0      812    OPEN       [Cancel]     │
│ 10:15  WIPRO      BUY  LIMIT   60     0      452    REJECTED   —            │
│        └ insufficient margin                                                │
└────────────────────────────────────────────────────────────────────────────┘
```

**Handle:**
- **Partial fills are a first-class state**, not a rounding detail. Show `filled/qty` always
- Rejection reason from the broker shown inline, verbatim
- Cancel is optimistic in the UI but confirmed by the broker's order update — show `CANCEL_PENDING`
  until confirmed. Never assume a cancel succeeded
- An order stuck `PENDING` beyond ~30s gets a warning: it may have reached the exchange
- A manual cancel of a bot order must be recorded as an operator action in the journal

`GET /api/orders` · `POST /api/orders/{id}/cancel` · `PUT /api/orders/{id}` (modify)

---

### 5.8 Positions

```
┌─ Positions ────────────────────────── [Square off all] ───────────────────┐
│ SYMBOL      PROD  QTY   AVG     LTP     UNREAL    REAL    STRATEGY  ACTION │
│ RELIANCE    MIS    50  2,842  2,868   +₹1,300      —    ema-cross  [Exit] │
│ INFY        MIS   -75  1,510  1,504     +₹450      —    mean-rev   [Exit] │
│ TATAMOTORS  MIS    40    978    964     -₹570      —    ema-cross  [Exit] │
│ ITC         CNC   200    412    438   +₹5,200      —    manual      —     │
├───────────────────────────────────────────────────────────────────────────┤
│ Intraday auto square-off at 15:15 · 2h 31m remaining                      │
└───────────────────────────────────────────────────────────────────────────┘
```

**Handle:**
- Separate MIS (auto-closed today) from CNC/NRML (carried). Show the square-off countdown for MIS
- **Square off all** requires a confirmation modal that lists exactly what will close and the
  approximate P&L impact. This is the most destructive button in the app
- Positions the bot did not open (manual Kite trades) must appear, marked `manual`, and be excluded
  from strategy attribution — but included in total exposure
- Reconciliation drift → the row is flagged, and the broker's number is shown as authoritative

`GET /api/positions` · `POST /api/positions/{id}/exit` · `POST /api/positions/square-off-all`

---

### 5.9 Journal

Historical record — the screen used at 21:00, not 12:00. Filterable by date range, strategy, symbol,
and outcome. One row per completed trade: entry, exit, holding time, P&L, the strategy and parameter
version, and links to the originating signal and order. CSV export.

`GET /api/journal?from=&to=&strategy=&symbol=` · `GET /api/journal/export.csv`

---

### 5.10 Settings → Risk

```
┌─ Risk limits ──────────────────────────────────── [Save] ─────────────────┐
│ Daily loss cap            [ ₹5,000 ]   halts the bot when breached        │
│ Max daily profit stop     [        ]   optional — stop when ahead         │
│ Risk per trade            [ 1.0 % ]    of allocated capital               │
│ Max concurrent positions  [ 3 ]                                           │
│ Max exposure              [ 60 % ]     of total capital                   │
│ Max orders per minute     [ 10 ]       runaway-loop guard                 │
│ Square-off time           [ 15:15 ]    IST, before broker forced close    │
│                                                                           │
│ Trading mode      ( ) PAPER    ( ) LIVE                                   │
│ Switching to LIVE requires typing LIVE to confirm.                        │
└───────────────────────────────────────────────────────────────────────────┘
```

**Handle:** loosening a limit mid-session is an audited event · lowering the daily loss cap below the
current loss halts immediately · going live is a typed confirmation, never a toggle click.

`GET/PUT /api/settings/risk` · `POST /api/settings/mode`

---

## 6. Live data to the browser

The dashboard is only useful if it is genuinely live.

**Use one WebSocket** from browser to your server (`/ws/stream`), not one per widget, and not polling.
Server pushes a typed envelope:

```json
{ "type": "TICK",        "data": { "token": 738561, "ltp": 2868.4, "ts": "…" } }
{ "type": "POSITION",    "data": { … } }
{ "type": "ORDER",       "data": { "clientOrderId": "…", "status": "COMPLETE", … } }
{ "type": "SIGNAL",      "data": { … } }
{ "type": "BOT_STATE",   "data": { "state": "HALTED", "reason": "daily loss cap" } }
{ "type": "HEALTH",      "data": { "socket": "OK", "token": "OK", "lastTick": "…" } }
```

Rules that matter:
- **Throttle ticks to the browser** — coalesce to ~4 updates/sec per instrument. Rendering 50 ticks
  a second burns battery and reads as flicker, not information.
- **Never fan raw broker ticks straight through.** Push only what the UI subscribes to.
- **Reconnect with backoff**, and on reconnect **refetch state via REST** rather than assuming the
  push stream filled the gap.
- **Show the connection state.** A frozen dashboard that looks live is dangerous.

Simpler alternative if WebSocket is too much for Phase 3: **SSE** (`GET /api/stream`, one-way) covers
everything above — the browser never needs to push. Upgrade later if you add interactive controls.

---

## 7. Cross-cutting UI rules

**Money and numbers**
- Indian grouping: `₹1,84,200`, not `₹184,200`
- P&L: green positive, red negative, **plus an arrow or sign** — colour alone fails for colour-blind
  users and in screenshots
- Always show the sign explicitly: `+₹4,280` / `-₹570`
- Round for display, never for calculation. Server sends exact decimals; the UI formats

**Time**
- Everything in IST, labelled. Never show a naive timestamp
- Relative for recency ("0.4s ago"), absolute for records ("12:31:04")
- Market-hours banner: pre-open · open · closed · holiday

**Destructive actions** — square off all, go live, disable a strategy holding positions, clear halt —
all get a modal stating exactly what will happen, with the count and dirty value. Going live gets a
typed confirmation.

**Errors** — every failed action says what failed, why, and what to do. "Order rejected: insufficient
margin — ₹12,400 required, ₹8,100 available." Never a bare toast saying "Error".

**Empty states** teach: an empty signal feed says "No signals yet today. ema-cross is watching 3
instruments," not "No data."

**Accessibility** — keyboard reachable kill switch, focus states, ARIA live region for state changes.
You will use this app tired and stressed.

---

## 8. Domain rules you must handle

The part that is easy to miss and expensive to learn live. Verify each against current Zerodha and
exchange documentation before relying on it — these change.

**Instruments and symbols**
- Kite keys everything on numeric `instrument_token`, not symbol. Always resolve through the daily dump
- F&O contracts expire; a token valid today is dead next month. Handle rollover explicitly
- Lot sizes are mandatory for F&O — order quantity must be a multiple. Reject at signal time, not at
  the broker
- Corporate actions (splits, bonuses) change price series discontinuously. Your indicators will emit
  garbage across the event unless you handle or skip it
- Exchanges have **freeze quantity** limits per order; large orders must be sliced

**Order mechanics**
- Product types: `MIS` (intraday, leveraged, force-closed) · `CNC` (delivery equity) · `NRML`
  (F&O overnight). Pick per strategy; the wrong one silently changes your risk
- Order types: `MARKET` · `LIMIT` · `SL` · `SL-M`. Market orders on illiquid scrips will fill far
  from LTP — prefer limit with a tolerance band
- Circuit limits: an order outside the band is rejected by the exchange, not the broker
- Post-close and AMO orders behave differently. Know which window you are in

**Money**
- Margin is checked at placement. Insufficient margin is a rejection you must handle, not a crash
- Charges (brokerage, STT, exchange fees, GST, stamp duty) are real and material for intraday. P&L
  without them is fiction — either model them or label the number "gross"
- MIS leverage means a small adverse move can exceed your notional capital

**Time and calendar**
- Market: 09:15–15:30, pre-open 09:00–09:15, holidays published annually by NSE
- Special sessions (Muhurat, mock) exist and will surprise a naive weekday check
- Never trade in the first minute or last five unless the strategy is explicitly designed for it —
  spreads are wide and moves are noise

**Platform limits** (verify current numbers)
- Per-endpoint rate limits, plus daily order caps per API key
- WebSocket: limited connections per key, limited instruments per connection
- Historical data API is a **paid add-on** — plan Phase 6 backtesting around that cost

**Compliance** — SEBI's retail algo framework governs API-originated automated orders, including
registration above certain order-rate thresholds. Check the current circulars and Zerodha's algo
policy before running live. This is a real constraint, not boilerplate.

---

## 9. Frontend architecture

**Recommendation: React + TypeScript + Vite**, separate from the Spring app in dev, built to static
assets that Spring serves in prod (one deployable, no CORS).

| Concern | Choice | Why |
|---|---|---|
| Framework | React 18 + TypeScript | Live-updating dashboard with lots of derived state |
| Build | Vite | Fast, and `dist/` drops into `src/main/resources/static/` |
| Server state | TanStack Query | Caching, refetch-on-reconnect, stale handling — all of which you need |
| Live data | Native WebSocket + a small typed event bus into the query cache | |
| Styling | Tailwind + shadcn/ui | Tables, modals, forms without building a design system |
| Charts | Recharts | Equity curve and P&L; not candlesticks — link to Kite for those |
| Routing | React Router | |
| Forms | React Hook Form + Zod | Strategy params need real validation, mirrored server-side |

**If you would rather not run a JS build**: Thymeleaf + htmx + SSE genuinely works for this app and
keeps everything in one Maven build. You lose ergonomics on the live-updating tables. Pick this only
if the frontend is a chore you want to minimise rather than a thing you want to be good.

**Do not** build the UI before Phase 3. A dashboard with nothing to display is wasted effort — build
against real ticks.

---

## 10. API surface

```
Auth
  GET    /login                              → 302 to Kite
  GET    /kite/callback                      → establishes app session
  POST   /api/auth/logout
  GET    /api/auth/session                   → { user, tokenExpiresAt, valid }

Bot control
  GET    /api/bot/state                      → { state, since, reason }
  POST   /api/bot/kill                       → HALTED
  POST   /api/bot/clear-halt
  POST   /api/bot/square-off-all

Instruments & watchlist
  GET    /api/instruments/search?q=
  GET    /api/watchlist
  POST   /api/watchlist        { instrumentToken }
  DELETE /api/watchlist/{token}

Strategies
  GET    /api/strategies
  POST   /api/strategies
  GET    /api/strategies/{id}
  PUT    /api/strategies/{id}
  POST   /api/strategies/{id}/enable
  POST   /api/strategies/{id}/disable
  GET    /api/strategies/{id}/performance?from=&to=

Signals / orders / positions
  GET    /api/signals?from=&to=&strategy=&outcome=
  GET    /api/signals/{id}
  GET    /api/orders?from=&to=&status=
  POST   /api/orders/{id}/cancel
  PUT    /api/orders/{id}
  GET    /api/positions
  POST   /api/positions/{id}/exit

Reporting
  GET    /api/dashboard/summary
  GET    /api/journal?from=&to=&strategy=&symbol=
  GET    /api/journal/export.csv
  GET    /api/pnl/daily?from=&to=

Settings & ops
  GET    /api/settings/risk
  PUT    /api/settings/risk
  POST   /api/settings/mode      { mode: PAPER|LIVE, confirm: "LIVE" }
  GET    /api/health/detail
  GET    /actuator/health

Streaming
  WS     /ws/stream
```

Conventions: JSON only · money as decimal strings, never floats · timestamps ISO-8601 with offset ·
errors as `{ "code", "message", "details" }` with a stable machine-readable `code` · all mutating
endpoints authenticated and audited.

---

## 11. Definition of done, per phase

Ship nothing to the next phase until these hold.

| Phase | Done when |
|---|---|
| **0** Fixes | Login completes end-to-end; a second login updates rather than duplicates the user; no secret in any tracked file; no token in any HTTP response body |
| **1** Foundation | `/actuator/health` correctly reports broker token state; Flyway owns the schema; tokens encrypted at rest; one security filter chain |
| **2** Adapter | An endpoint returns your real Zerodha portfolio; instrument dump loads once daily; rate limiter provably caps request rates |
| **3** Market data | 1-minute bars for the watchlist match Kite's chart; socket survives a forced disconnect and resubscribes; staleness is detected and surfaced |
| **4** Strategy + risk | A full day of paper signals, each with a reconstructible explanation; every risk rule provably vetoes; kill switch halts within one second |
| **5** Execution | Reconciliation runs clean for five consecutive sessions; a simulated broker timeout produces no duplicate order; square-off closes everything before 15:20 |
| **6** Live | One instrument, minimum size, tightest caps. Scale only after daily reconciliation against the Zerodha console matches for two weeks |

---

## 12. Where to start tomorrow

1. **Rotate the Zerodha API secret.** It is in git history. Nothing else matters until this is done.
2. Fix the five auth defects in [ARCHITECTURE.md §A.2](ARCHITECTURE.md#a2-blocking-defects) — that is
   Phase 0 and it is roughly a day of work.
3. Add Flyway and write the initial migration from the schema in
   [§B.7](ARCHITECTURE.md#b7-data-model). Do this before there is data to migrate.
4. Build the read-only broker adapter (Phase 2) and prove it with a `/api/positions` endpoint hitting
   your real account. That single working endpoint validates auth, config, HTTP, and mapping at once.

Only then start on the UI, and start with the app shell and dashboard — the two surfaces from §2 that
you would actually miss.
