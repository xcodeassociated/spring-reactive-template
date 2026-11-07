# ADR-0002: SSE auth via shared one-time tickets + stream lifecycle enforcement

- **Status:** Superseded by ADR-0007
- **Date:** 2026-11-01
- **Context:** Resolves the `// todo: make this endpoint secure` on `/update/**`; builds on ADR-0001 (WebSocket one-time tickets). Package names follow the ADR-0003 restructure (`notifications/`).

## Context

The SSE user-update stream (`GET /update/user`, `notifications/sse`) was initially public.
Handshake-only bearer auth (Spring Security `.authenticated()`) fixed the anonymous case but left
three gaps:

1. **Browsers cannot send headers on `EventSource`** (WHATWG HTML §9.2.2) — the same constraint
   ADR-0001 solved for WebSocket upgrades.
2. **The stream outlives its token.** Security is evaluated once, at the request; a JWT that
   expires mid-stream leaves the stream streaming (SignalR docs: *"by default the connection
   continues to work"*). With Keycloak, session kill does not revoke issued access tokens — `exp`
   is the only passive revocation boundary.
3. **Logout must actively close live streams**, and SSE has no close codes — after `200`, the only
   signals are named events, stream completion, and the HTTP status of the next reconnect.

Also SSE-specific: `EventSource` **auto-reconnects by replaying the same URL** (plus
`Last-Event-ID`), which interacts with single-use credentials.

## Decision

Extend the WebSocket ticket mechanism into shared infrastructure and apply the
`sse-auth-session-lifecycle` skill's Policy A (close-at-exp + reconnect).

1. **Shared ticket infra → `notifications/ticket/`**: `TicketStore` (256-bit SecureRandom, SHA-256-hashed
   keys, atomic compare-and-delete redeem, TTL), `TicketRecord`, `TicketAuthentication` (rebuilds
   the security principal from mint-time claims), `TicketSupport.isOriginAllowed`,
   `TicketProperties` (`com.softeno.ticket.*`). Per-process in-memory: a crash leaves no orphans;
   a scheduled sweeper evicts expired entries even when idle. Multi-instance would require
   externalizing (Mongo TTL index was considered and deferred — see Alternatives).
2. **Tickets are transport-bound**: `TicketRecord.origin ∈ {WEBSOCKET, SSE}`; redeem enforces the
   match — an SSE ticket can never open a WebSocket and vice versa. Also visible in logs for
   debugging.
3. **SSE handshake**: `POST /sse-ticket` (authenticated POST — a GET mint would be a cross-site
   mint primitive, RFC 10017 §6.1.3.3) mints an origin=SSE ticket; `SseTicketRedeemFilter` redeems
   `GET /update/user?ticket=…` before any body bytes (401 pre-body on missing/expired/replayed/
   wrong-origin; Origin allowlist exact-match). Requests without `ticket` pass through — the bearer
   path stays for API/service clients.
4. **Stream lifecycle** (`SseSubscriptionRegistry`): each stream registers `{sub, exp}` + a reverse
   index `sub → streams`; a sweeper (`com.softeno.sse.sweep-interval-seconds`) enforces the token
   ceiling hard: past `exp` → emit `event: token_expired` → complete the stream. Reconnects are
   fresh handshakes and re-run auth.
5. **Logout**: the Keycloak→Kafka `LOGOUT` consumer now revokes **both** transports:
   WebSocket sessions (4403, ADR-0001) and SSE streams (`event: logged_out` → complete; the client
   must stop — reconnecting would hit auth again anyway).
6. **Reconnect contract**: tickets are strictly single-use; browser clients re-mint on `error`
   before reconnecting (windowed redeem was rejected — larger replay surface, see skill).

Termination-event contract (the stream analog of the WS 4401/4403 codes):

| Event | Cause | Client behavior |
| --- | --- | --- |
| `token_expired` | ceiling passed | refresh + re-mint + reconnect |
| `logged_out` | logout/revocation | stop; logged-out UI |

## Consequences

- `notifications/sse` imports `notifications/ticket` (same-feature dependency, no new coupling);
  `KafkaKeycloakController` gains the SSE registry dependency.
- New config keys: `com.softeno.ticket.{ttl-seconds,sweep-interval-seconds,allowed-origins}`,
  `com.softeno.sse.sweep-interval-seconds`; `com.softeno.ws.ticket-ttl-seconds` moved to
  `com.softeno.ticket.ttl-seconds`.
- `/ws-ticket`, `/sse-ticket`, `/ws/**`, `/update/**` are `.authenticated()` in `SecurityConfig`.
- The stream ceiling is `sweepInterval`-granular (exposure window: `exp + sweepInterval`).
- Single-instance assumption for ticket/subscription registries (documented, same as ADR-0001).
- Removing the SSE feature = delete `notifications/sse/` + `com.softeno.sse.*` + the
  `/update/**`, `/sse-ticket` rules; removing both transports then also drops `notifications/ticket/`.

## Alternatives considered

- **Mongo-backed ticket store + TTL index** — DB-visible, crash-proof cleanup, multi-instance;
  deferred as overkill for a single-instance template (in-memory already leaves no crash orphans).
- **Cookie/session (BFF)** — RFC 10017-preferred for browsers generally, but an architecture
  change beyond SSE's scope.
- **`?access_token=<jwt>`** — rejected: RFC 9700 §4.3.2 MUST NOT.
- **Fetch-based SSE with rotating bearer headers** — cleanest re-auth, but re-implements
  EventSource; kept as the skill's documented alternative.
- **Windowed ticket redeem** — keeps native auto-reconnect working untouched, but widens the replay
  surface; re-mint-on-error preserves single-use.
