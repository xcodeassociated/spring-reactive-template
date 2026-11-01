# Prompt for the frontend OpenCode instance

Paste everything below the line into the other instance.

---

You are working in the **frontend** repository. Its backend (Spring Boot WebFlux, Kotlin) just got
a breaking API update on branch `feat/ws-ticket-auth` (not yet merged to its main). Your job:
adapt this frontend to the new backend contract, verify it end-to-end, and do not
introduce regressions in features that were already working.

## Backend environment (local dev)

- API: `http://localhost:8080` (REST `/coroutine/*`, GraphQL `/graphql`, SSE `/update/user`, WS `/ws`)
- Keycloak: `http://localhost:8090`, realm `app`, client `frontend`; dev users `user` / `user2`,
  password `KoalkaNaDrzewko1812?` (dev only). The frontend origin must be
  `http://localhost:3000` (or `:8080`) — those are the origins the backend's ticket
  allowlist accepts and Keycloak's `frontend` client is configured for.
- The backend repo contains the authoritative change list: `docs/api-changes-vs-main.md`,
  reference clients `web/chat.html` and `web/sse.html`, and ADRs 0001/0002/0004/0005 in `docs/adr/`.
  If anything below is ambiguous, read those files (mount/copy the backend repo or ask me for it) —
  do not guess.

## Reference implementations — the backend repo's `web/` folder (read these first)

The two files below are complete, working implementations of the exact contracts you must port.
They are also runnable against the live backend at
`http://localhost:8080/web/chat.html` and `http://localhost:8080/web/sse.html`
(log in as `user`) — open them in a browser and watch the behavior if any flow is unclear.

- **`web/chat.html`** — the WebSocket client contract:
  - `mintTicket()` — `POST /ws-ticket` with bearer, returns the single-use ticket.
  - `connect()` — always mints a FRESH ticket, then `new WebSocket(ws://…/ws?ticket=…)`;
    never reuses a socket URL.
  - `onmessage` — auto-answers `ping` with `{"to":"SYSTEM","content":"pong"}`; skips the
    `HANDSHAKE` frame; the first `SYSTEM` frame's `content` is your user id.
  - `onclose` — **4403** → stop + logged-out UI; **4401** → `kc.updateToken` → re-mint → reconnect;
    any other drop → reconnect with a fresh ticket (1.5 s backoff in `scheduleReconnect()`).
- **`web/sse.html`** — the SSE client contract:
  - `subscribe()` — mint via `POST /sse-ticket`, then `new EventSource("/update/user?ticket=" + …)`.
  - named listeners: `heartbeat`, `update`, `token_expired` (→ refresh → re-mint → reconnect),
    `logged_out` (→ close, stop, logged-out UI).
  - `onerror` — closes the EventSource (kills the browser's used-ticket auto-retry loop),
    re-mints, reconnects with backoff.

Port the *behavior* into this app's idioms (its own HTTP client, state management, UI components) —
do not vendor the vanilla-JS pages themselves.

## What changed (summary — details in docs/api-changes-vs-main.md)

1. **Removed**: all `/reactive/**` REST endpoints (identical JSON now lives under `/coroutine/*`)
   and `GET /update/user/reactive`. Replace any `/reactive/` usage with the `/coroutine/` equivalent.
2. **SSE `GET /update/user` is no longer public.** API-style clients send `Authorization: Bearer`.
   Browsers cannot set headers on `EventSource`, so the flow is:
   `POST /sse-ticket` (with bearer) → `{"ticket","expiresIn":10}` → `new EventSource("/update/user?ticket=" + ticket)`.
   The ticket is **single-use** — on `error` you must `close()` the EventSource, re-mint, and reconnect
   after a short backoff (auto-reconnect replays the used ticket → 401 loop).
   New named events: `token_expired` (refresh token, re-mint, reconnect) and
   `logged_out` (stop, show logged-out UI — never reconnect). `heartbeat`/`update` events unchanged.
3. **WebSocket `/ws`** — no longer `ROLE_ADMIN`, now any authenticated user, and browsers connect via
   a one-time ticket: `POST /ws-ticket` (bearer) → `new WebSocket("ws://localhost:8080/ws?ticket=" + t)`.
   Frame format unchanged (`{from,to,content}`; on connect you get a `HANDSHAKE` frame then a frame
   whose `content` is your user id; answer `ping` with `{"to":"SYSTEM","content":"pong"}`).
   New close contract: **4401** (`REAUTH_REQUIRED` — refresh, re-mint, reconnect) and
   **4403** (`LOGGED_OUT` — stop). Reconnects always mint a **fresh** ticket. A foreign `Origin`
   gets HTTP 403 pre-upgrade; expired/replayed ticket → 401.
4. **New endpoints**: `POST /ws-ticket`, `POST /sse-ticket` (require bearer, no body).
5. **Unchanged** (do not churn them): all `/coroutine/users*` + `/coroutine/permissions*` JSON shapes
   (`_id`, `role`, `version`, audit fields; PUT requires `version`), the GraphQL schema and all
   operations, and the error envelope `{timestamp, errorCode "E000"|"E100", message, status, error}`.
   `/external/**` 503 carries `errorCode: "E100"`.

## Task

1. Inventory the current API surface in this repo: grep for `:8080`, `/reactive/`, `/coroutine/`,
   `/update/`, `/graphql`, `EventSource`, `WebSocket`, `ws://`, `wss://`, ticket/auth handling.
   List every call site before changing anything.
2. Update the HTTP layer: drop `/reactive/*`, keep JSON contracts as-is.
3. Implement the ticket flow for browser WS and SSE (mint helpers, single-use discipline,
   re-mint-on-reconnect). Put it in one shared module/service, not duplicated per component.
   Mirror the behavior of the `web/` reference files listed above.
4. Implement the stream-lifecycle contract: handle `token_expired`, `logged_out`,
   close codes 4401/4403, and the 401/403 pre-connect failures with sensible UI states
   (reconnecting / logged-out). Refresh the Keycloak token proactively where the app already does.
5. Never place an `access_token` in any URL — only the opaque single-use `ticket`.
6. Tests: adapt existing API tests/mocks to the new endpoints and add coverage for
   ticket minting, reconnect-with-fresh-ticket, and the two termination events
   (mock the EventSource/WebSocket where the test runner can't run a real browser).
   Run the repo's build + test commands and make them pass.
7. If the repo has E2E/browser capability (Playwright etc.), verify live against
   `localhost:8080`: login → subscribe (SSE) → trigger a user creation → event arrives;
   WS connect → HANDSHAKE + id frame; logout elsewhere → `logged_out`/4403.

## Constraints

- Keep the app's existing stack/conventions; do not introduce a new HTTP client or state library
  to satisfy this.
- Do not commit unless asked; report changed files, test results, and anything that still needs
  a product decision (e.g. where to show the logged-out state).
- If you find a call site whose new behavior is ambiguous (e.g. retry policy for 500s from the
  generic error handler), stop and ask instead of guessing.
