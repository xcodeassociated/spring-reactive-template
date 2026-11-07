# ADR-0007: SSE auth via bearer-over-fetch; one-time tickets are WebSocket-only

- **Status:** Accepted
- **Date:** 2026-11-06
- **Context:** Supersedes the SSE half of ADR-0002 (the WebSocket ticket decision, ADR-0001, stands).

## Context

ADR-0002 put SSE behind the shared one-time-ticket handshake because browsers cannot send headers
on `EventSource`. Two things changed the calculus:

1. The FE stream client no longer needs `EventSource`: a `fetch`-based SSE reader can send an
   `Authorization` header like every other request, and the app already manages reconnects itself
   (EventSource's own auto-reconnect had to be disabled under the ticket scheme anyway — it
   replays the redeemed ticket).
2. The in-memory `TicketStore` is per-instance state: on a multi-instance deployment the mint and
   the redeem can land on different pods, so the SSE path needed externalized state (or sticky
   routing) that the WebSocket path does not force on any other endpoint.

## Decision

1. **SSE authenticates with a bearer JWT.** The FE `useSimpleSSE` streams `GET /update/user` via
   `fetch` with `Authorization: Bearer` (token resolved per connect/reconnect through
   `getAccessToken()` — proactive refresh). No credential ever appears in a URL.
2. **Tickets are WebSocket-only.** Deleted: `POST /sse-ticket`, `SseTicketRedeemFilter`,
   `TicketOrigin.SSE`, and the `web/sse.html` dev page. `notifications/ticket/` remains shared
   infrastructure but has a single consumer (ADR-0001).
3. **The stream lifecycle contract from ADR-0002 is unchanged and binding**: named `token_expired`
   / `logged_out` control events, token-ceiling sweeper (`exp` of the authenticated principal into
   `SseSubscriptionRegistry`), heartbeat liveness, Keycloak→Kafka logout revocation via
   `revokeUser`, and the client contract (reconnect with a freshly resolved token, stop forever on
   `logged_out`).

## Consequences

- `/update/user` has exactly one auth path (the resource-server JWT), so SSE works on any instance
  with zero shared state; the `TicketStore` multi-instance caveat now only affects `/ws/**`.
- The FE keeps owning reconnect/backoff (unchanged from ADR-0002's client contract), now without
  the per-connect mint roundtrip — reconnect latency drops by one HTTP call.
- A revoked-but-still-open stream is unaffected: expiry is still enforced by the sweeper from the
  connect-time `exp`, not by header re-validation (a stream is a single request; headers cannot
  rotate mid-body). Re-auth therefore still means reconnect — same contract clients already
  implement.
- Origin/CSWSH protection for the stream moves to the standard CORS + bearer story: an
  unauthenticated cross-site `fetch` gets 401 from the security chain (opaque-response CORS rules
  prevent reading it); the ticket-era `TicketSupport.isOriginAllowed` check remains on the WS
  redeem path.
