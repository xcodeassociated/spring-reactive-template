# API changes: `feat/ws-ticket-auth` vs `main`

For frontend integration. Backend: `http://localhost:8080`; Keycloak: `http://localhost:8090`,
realm `app`, client `frontend`. Verified against the running stack (REST + GraphQL + WS + SSE).

## TL;DR

| Area | main | this branch |
|---|---|---|
| `/reactive/**` REST | existed (users/permissions) | **GONE** — use `/coroutine/*` |
| `GET /update/user` (SSE) | **public** | **authenticated** (bearer or ticket) |
| `GET /update/user/reactive` | existed | **GONE** |
| `WS /ws` | ROLE_ADMIN, bearer-header-only (browsers could not connect) | any authenticated user; browsers use one-time ticket |
| new endpoints | — | `POST /ws-ticket`, `POST /sse-ticket` |
| WS lifecycle | connect frames + ping/pong | + control contract: close 4401/4403, `REAUTH_REQUIRED`/`LOGGED_OUT` frames, Origin allowlist (403) |
| SSE lifecycle | `heartbeat`, `update` | + `token_expired`, `logged_out` named events; ticket single-use ⇒ re-mint on reconnect |
| revocation sources | user-initiated logout only | + admin-initiated session termination / user delete revoked instantly via `keycloak-admin-events` listener |
| `/coroutine/*` JSON, GraphQL schema, error envelope | — | **byte-identical, no changes** |

## 1. Removed (breaking)

- **`/reactive/**`** — the entire reactive API flavor (`/reactive/users`, `/reactive/permissions`, …).
  The coroutine endpoints are the single API now; request/response JSON of the reactive variants was
  identical to `/coroutine/*`, so a find-replace of the prefix is sufficient.
- **`GET /update/user/reactive`** — removed; `GET /update/user` covers it.

## 2. Auth model changes

| Path | main | branch |
|---|---|---|
| `/coroutine/**`, `/graphql/**`, `/external/**` | `ROLE_ADMIN` | `ROLE_ADMIN` (unchanged) |
| `/ws/**` | `ROLE_ADMIN` + bearer header | **`authenticated()`** — bearer header **or** one-time ticket; browsers cannot send headers on a WS upgrade, so use the ticket flow |
| `/update/**` (SSE) | permitAll | **`authenticated()`** — bearer **or** ticket |
| `POST /ws-ticket`, `POST /sse-ticket` | — | new; require bearer |
| `/web/**` | — | new; permitAll static dev pages (`/web/chat.html`, `/web/sse.html`) |

Unauthorized stream/handshake attempts fail **before** connect/first byte: HTTP 401
(bad/missing/expired/replayed ticket), 403 (Origin not in the server's
`com.softeno.ticket.allowed-origins` / `com.softeno.ws.allowed-origins` — dev default
`http://localhost:3000` + `http://localhost:8080`). CORS never applies to WS/SSE handshakes —
the Origin allowlist does.

## 3. New endpoints

### `POST /ws-ticket` and `POST /sse-ticket`
Request: no body, `Authorization: Bearer <access_token>`.
Response `200`: `{"ticket": "<opaque url-safe base64>", "expiresIn": 10}`

Rules: single-use, 10 s TTL, transport-bound (an SSE ticket cannot open a WS and vice versa),
never reuse a ticket (reconnect ⇒ new mint). Never put an `access_token` in a query string.

### Browser WS connect flow
```
POST /ws-ticket (bearer)              -> { ticket }
new WebSocket("ws://localhost:8080/ws?ticket=" + ticket)
```
### Browser SSE subscribe flow
```
POST /sse-ticket (bearer)             -> { ticket }
new EventSource("http://localhost:8080/update/user?ticket=" + ticket)
```

## 4. WebSocket contract (`/ws`)

Frames are JSON `{"from": "<sub|null>", "to": "<sessionId|ALL|SYSTEM>", "content": "<text>"}` (unchanged shape).

| Moment | Server sends | Client should |
|---|---|---|
| connect | `HANDSHAKE` frame, then a frame whose `content` is your user id | start; `to:"ALL"` broadcasts |
| heartbeat | `content:"ping"` every 10 s | reply `{"to":"SYSTEM","content":"pong"}` |
| token ceiling reached mid-session | `content:"REAUTH_REQUIRED"` then close **4401** | refresh token, mint **fresh** ticket, reconnect |
| logout (Keycloak → Kafka) | `content:"LOGGED_OUT"` then close **4403** | stop reconnecting, show logged-out UI |
| plain drop / error | — | reconnect with a **fresh ticket** (replaying the URL 401s) |
| Origin not allowed | pre-upgrade HTTP 403 | — |

`from` on relayed messages is stamped server-side from the session identity; client-sent `from` is ignored.

## 5. SSE contract (`GET /update/user`)

| Event | Data | Meaning / client action |
|---|---|---|
| `heartbeat` | `{"from":"SYSTEM","to":"ALL","content":"ping"}` | stream alive (immediately on subscribe, then ~10 s) |
| `update` | `{"from":"SYSTEM","to":"ALL","content":"USER_CREATED: <id>"}` / `USER_UPDATED: <id>` / `USER_DELETED: <id>` | domain broadcast (user lifecycle, ADR-0006) |
| `token_expired` | `…content:"token_expired"…` | mint-time token ceiling reached — refresh, re-mint, reconnect |
| `logged_out` | `…content:"logged_out"…` | revoked — close, stop reconnecting |

EventSource auto-reconnect replays the URL — the ticket in it is already redeemed ⇒ 401 loop.
On `error`: close the EventSource, re-mint a ticket, reconnect after a backoff.
API/service clients may keep using a plain `Authorization: Bearer` header on the stream GET (no ticket).

## 6. Unchanged — no FE work needed

- `POST/PUT/GET/DELETE /coroutine/users*`, `/coroutine/usersCount`, `/coroutine/permissions*` —
  identical JSON: `_id`, `name`, `email`, `role` (array; `role` doubles as the permission-ids field on
  input), `version`, `createdBy/createdDate/modifiedBy/modifiedDate`. PUT requires `version`
  (optimistic locking) — as before.
- **GraphQL** (`POST /graphql`): schema byte-identical (diff empty). Queries `getAllUsers`,
  `getUser`, `getUsersSize`, `getAllPermissions`, `getPermission`; mutations `create/update/deleteUser`,
  `create/update/deletePermission`. `User.permissions` now comes from embedded snapshots — same output fields.
- Error envelope: `{timestamp, requestId, errorCode: "E000", message, status, error}`;
  `E100`/503 for external-service failures. (500 for not-found/version-missing — template's generic handler, unchanged.)
- `/sample/**`, `/external/**` (incl. `/external/grpc/{data}`), `/async/**`, actuator, GraphiQL, Swagger — unchanged.

## 7. Reference implementations in this repo

- `web/chat.html` — full WS client contract (ticket, reconnect, 4401/4403 handling).
- `web/sse.html` — full SSE client contract (re-mint on error, token_expired, logged_out).
- `http/ws.http`, `http/update.http` — IntelliJ HTTP Client flows.
