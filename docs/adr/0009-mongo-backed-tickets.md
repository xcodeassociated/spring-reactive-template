# ADR-0009: DB-backed one-time tickets (Mongo TTL) — multi-instance-ready handshake

- **Status:** Accepted
- **Date:** 2026-11-06
- **Context:** Closes the remaining multi-instance gap named by ADR-0008 ("the in-memory `TicketStore` … fix when WebSocket goes multi-instance"). ADR-0001's ticket design stands; only the store's durability boundary changes.

## Context

Tickets are minted by an authenticated REST call and redeemed by a separate
WebSocket-upgrade connection. Two different requests — they can land on different pods.
The in-memory `ConcurrentHashMap` store made redemption a same-process affair: mint on pod A,
upgrade on pod B → unknown ticket → 401. A chat client behind a load balancer would fail
handshakes at roughly `(N-1)/N` rate.

Options considered:
- **Stateless signed ticket (HMAC/JWT):** verification is instance-free, but a signed token is
  replayable within its TTL — and single-use is the *entire* justification for carrying a
  credential in a URL (RFC 9700 §4.3.2). Fixing replay needs a seen-jti store → a database again,
  plus key management. Rejected.
- **Redis:** right semantics, but a whole new service to operate for one 10-second-lived document
  per connect. Rejected (KISS / on-prem minimal-services).
- **Sticky routing** for `/ws-ticket` + `/ws`: fragile (two different connection types), and it
  pins the topology. Rejected.

## Decision

1. **`TicketStore` is a port (interface); the production adapter is Mongo-backed**
   (`MongoTicketStore`). `_id` = SHA-256(raw ticket) — the raw ticket is never persisted or logged
   (unchanged from ADR-0001/0002).
2. **Redeem = `findAndRemove`** — the atomic compare-and-delete now spans the cluster: exactly one
   winner across ALL instances, replay on any other pod fails. Same contract as the old
   `ConcurrentHashMap.remove`, shared durability.
3. **Expiry moves to a TTL capped-at index** (`@Indexed(expireAfter = "0s")` on `deleteAt`): the
   database evicts dead tickets on every instance with no sweeper code, crash-orphan-free. The
   in-process sweeper coroutine is deleted. Redeem still re-checks `deleteAt`/`exp` in code — the
   TTL monitor is periodic (≈60 s), so the index is the janitor and the code check is the gate.
4. **The technical collection bypasses domain conventions**: no `BaseDocument`, no audit fields, no
   `@Version` — it is coordination state, not a domain entity.

## Consequences

- Multi-instance WS handshakes work with plain round-robin load balancing; no Redis, no stickiness.
- Handshake path gains two Mongo ops (insert + findAndRemove) per connect — negligible at
  handshake rates; the DB is already on the request path everywhere else.
- `TicketStoreTest` became an IT against real Mongo (atomicity/TTL now provable against the real
  engine); the filter contract test uses an in-memory `FakeTicketStore` implementing the port.
- Cross-instance logout revocation depends on ADR-0008's per-instance groups; together they make
  the full WS auth story multi-instance: ticket shared, revocation fanned out.
- Verified live with two instances: mint on B → redeem on A; replay on either pod → 401; logout →
  both pods close their sockets with 4403.
