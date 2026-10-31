# Kite Connect — Authentication Flow

This document describes the end-to-end authentication flow used by **AlgoDesk** to log a user in via Zerodha Kite Connect, exchange tokens, persist them, and use them for subsequent API calls.

---

## 1. High-Level Overview

```
┌────────┐   /login    ┌────────────┐   redirect   ┌──────────────┐
│ Client │ ──────────▶ │ AlgoDesk   │ ───────────▶ │ Kite Login   │
│        │             │ /login     │              │ (kite.trade) │
└────────┘             └────────────┘              └──────┬───────┘
                                                          │ user logs in
                                                          ▼
┌────────┐  JWT/Session ┌────────────┐  request_token ┌──────────────┐
│ Client │ ◀──────────── │ /kite/     │ ◀──────────── │ Kite redirect│
│        │               │ callback   │                │ to AlgoDesk  │
└────────┘               └─────┬──────┘                └──────────────┘
                               │
                               ▼
                ┌──────────────────────────────┐
                │ generateSession(requestToken,│
                │     apiSecret)               │
                │  → access_token, public_token│
                └──────────────┬───────────────┘
                               ▼
            ┌──────────────────────────────────────┐
            │ 1. Fetch profile                     │
            │ 2. Upsert UserEntity                 │
            │ 3. Save AuthTokenEntity (active)     │
            │ 4. Issue app JWT / session           │
            │ 5. Redirect to frontend dashboard    │
            └──────────────────────────────────────┘
```

---

## 2. Step-by-Step Flow

### Step 1 — User hits `/login`
- AlgoDesk generates a random `state` (UUID) for CSRF protection.
- Stores it in `HttpSession` under `FieldConstants.FIELD_KIETE_STATE`.
- Builds the Kite login URL:
  ```
  https://kite.zerodha.com/connect/login?v=3&api_key={API_KEY}&state={STATE}
  ```
- Returns `302 Redirect` to the Kite login page.

### Step 2 — User authenticates on Kite
- User enters Kite credentials + 2FA on `kite.zerodha.com`.
- On success, Kite redirects to your registered **Redirect URL**:
  ```
  https://yourapp.com/kite/callback?request_token=XXXX&action=login&status=success&state=YYYY
  ```

### Step 3 — `/kite/callback` receives `request_token`
1. **Validate `state`** against the value stored in session — reject if mismatched.
2. **Exchange `request_token` for `access_token`**:
   ```java
   User kiteUser = kiteConnect.generateSession(requestToken, apiSecret);
   kiteConnect.setAccessToken(kiteUser.accessToken);
   kiteConnect.setPublicToken(kiteUser.publicToken);
   ```
3. **Fetch profile** to enrich user data:
   ```java
   Profile profile = kiteConnect.getProfile();
   ```

### Step 4 — Persist the user
Upsert into `users` table via `UserService.saveOrUpdateFromKite(kiteUser)`:
- Match by `user_broker_id` (Kite `user_id`, e.g. `QEX463`).
- Update `user_name`, `email`, `phone` if changed.
- Insert if new.

### Step 5 — Persist the auth token
In the `auth_tokens` table:
1. Mark all previous active rows for this user `is_active = false` and set `revoked_at`.
2. Insert a new row:
   | Field | Value |
   |---|---|
   | `user_id` | FK to `users.user_id` |
   | `access_token` | from Kite (encrypt at rest) |
   | `public_token` | from Kite |
   | `request_token` | from Kite (audit only) |
   | `api_key` | the app's Kite API key |
   | `issued_at` | `now()` |
   | `expires_at` | next day **06:00 IST** |
   | `is_active` | `true` |

### Step 6 — Issue an app-level session
- Generate **your own JWT** (or session cookie) tied to `userId`.
- **Never** return Kite's raw `access_token` to the browser.
- Redirect the user to the frontend dashboard:
  ```
  302 Location: /dashboard?token={appJwt}
  ```

---

## 3. Token Lifetime & Expiry

| Token | Lifetime | Notes |
|---|---|---|
| `request_token` | ~1 minute, single-use | Only used to obtain `access_token` |
| `access_token` | Until ~06:00 IST next day | Required for every Kite API call |
| `public_token` | Same as `access_token` | Required for KiteTicker (WebSocket) |
| `refresh_token` | Long-lived | Only for Kite Connect *subscription* users |

### Handling expiry
- Catch `com.zerodhatech.kiteconnect.kitehttp.exceptions.TokenException`.
- Mark token `is_active = false`.
- Force user to re-login via `/login`.

---

## 4. Using the Token for Subsequent API Calls

Wrap Kite client construction in a factory:

```java
public KiteConnect getClientFor(String userId) {
    AuthTokenEntity t = authTokenRepository
            .findByUserIdAndIsActiveTrue(userId)
            .orElseThrow(() -> new UnauthorizedException("Re-login required"));

    KiteConnect kc = new KiteConnect(kiteProperties.getApiKey());
    kc.setUserId(userId);
    kc.setAccessToken(decrypt(t.getAccessToken()));
    kc.setPublicToken(decrypt(t.getPublicToken()));
    return kc;
}
```

All services (`OrderService`, `MarketDataService`, etc.) should consume this factory rather than building `KiteConnect` directly.

---

## 5. Security Checklist

- [ ] `api_key` and `api_secret` loaded from env vars, **not** committed.
- [ ] `access_token` and `refresh_token` encrypted at rest (AES / Jasypt).
- [ ] `state` parameter validated to prevent CSRF on `/kite/callback`.
- [ ] Raw Kite tokens never exposed to the client/browser.
- [ ] App issues its own JWT/session with short expiry + refresh.
- [ ] HTTPS enforced on the redirect URL.
- [ ] Audit log on every token issuance & revocation.

---

## 6. Database Schema (recap)

### `users`
| Column | Type | Notes |
|---|---|---|
| `user_id` | BIGINT IDENTITY PK | internal id |
| `user_broker_id` | VARCHAR(20) UNIQUE | Kite `user_id` |
| `user_name` | VARCHAR(150) NOT NULL | |
| `email` | VARCHAR(150) UNIQUE | |
| `phone` | VARCHAR(20) | |
| `broker` | VARCHAR(50) DEFAULT 'ZERODHA' | |
| `created_at` / `updated_at` | TIMESTAMP | auto-managed |

### `auth_tokens`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `user_id` | FK → `users.user_id` | |
| `access_token` | VARCHAR(500) | encrypted |
| `public_token` | VARCHAR(500) | |
| `request_token` | VARCHAR(500) | audit |
| `refresh_token` | VARCHAR(500) | optional |
| `api_key` | VARCHAR(100) | |
| `issued_at` | TIMESTAMP | |
| `expires_at` | TIMESTAMP | next day 06:00 IST |
| `is_active` | BOOLEAN | |
| `revoked_at` | TIMESTAMP NULL | |

---

## 7. Sequence Diagram (textual)

```
Client → AlgoDesk:        GET /login
AlgoDesk → Client:        302 → kite.zerodha.com/connect/login?...&state=S
Client → Kite:            login + 2FA
Kite → Client:            302 → /kite/callback?request_token=R&state=S
Client → AlgoDesk:        GET /kite/callback?request_token=R&state=S
AlgoDesk → AlgoDesk:      validate state
AlgoDesk → Kite API:      POST /session/token (request_token, api_secret)
Kite API → AlgoDesk:      { access_token, public_token, user_id, ... }
AlgoDesk → Kite API:      GET /user/profile
Kite API → AlgoDesk:      { user_name, email, ... }
AlgoDesk → DB:            upsert users
AlgoDesk → DB:            deactivate old tokens, insert new auth_token
AlgoDesk → AlgoDesk:      issue app JWT
AlgoDesk → Client:        302 → /dashboard?token=JWT
```

---

## 8. Error Scenarios

| Scenario | Handling |
|---|---|
| `state` missing/mismatched | `400 Bad Request` — possible CSRF |
| `request_token` expired/invalid | `401 Unauthorized` — ask user to retry `/login` |
| Kite API down | `502 Bad Gateway` + retry with backoff |
| `TokenException` on later API call | Mark token inactive, redirect `/login` |
| DB write fails after token issued | Log + alert; token still valid in Kite, user can retry |

---

## 9. Future Enhancements

- Auto-revoke tokens at 06:00 IST via a scheduled job.
- Multi-broker support (Upstox, Dhan) using a `broker` discriminator.
- Refresh-token flow (Kite Connect subscription).
- Encrypted column type via Jasypt for `access_token`.
- Audit table `auth_token_events` for issuance/revocation history.

