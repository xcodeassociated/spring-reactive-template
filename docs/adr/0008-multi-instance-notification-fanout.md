# ADR-0008: Multi-instance notification fan-out via per-instance consumer groups

- **Status:** Accepted
- **Date:** 2026-11-06
- **Context:** Extends ADR-0006 (lifecycle broadcasts) and ADR-0004 (port fan-out) to N service instances.

## Context

Every Kafka consumer in this service is a *notification* consumer: user lifecycle events
(ADR-0006) broadcast to connected clients, and Keycloak auth/admin events that close live
sessions. All three listeners shared one consumer group (`KAFKA_GROUP`), which is competing-consumer
semantics — each event is delivered to **one** instance. With one instance that is invisible; with
N, every client connected to a pod that did not get the record simply never learns about the
change ("SSE works on some pods"), and a kill-session only closes the connections of whichever pod
consumed the logout event.

A naive per-instance group fix would also introduce a replay storm: the consumers were configured
with `auto-offset-reset=earliest`, and a brand-new group id (every pod start is brand-new) begins
at the topic head-to-tail — re-broadcasting the entire event history on every rollout.

## Decision

1. **Per-instance fan-out groups.** Each listener's group id is suffixed with
   `com.softeno.kafka.instance-id` = `${INSTANCE_ID:${HOSTNAME:standalone}}`. In Kubernetes
   `HOSTNAME` is the pod name automatically, so every instance consumes **every** event and acts on
   **its own** registries. Single-instance/dev keeps identical behavior (one member per group);
   local multi-process runs can override `INSTANCE_ID`.
2. **`auto-offset-reset=latest` for production consumers.** Fan-out groups start at the topic tail:
   a new pod must never replay history. Missing events while a pod was down are harmless because
   these events are cache-invalidation hints (ADR-0006 decision record), and the client's
   `onConnect` revalidation closes the gap. The integration-test profile deliberately keeps
   `earliest` (deterministic consumption during partition assignment).
3. **Invariant: fan-out listeners perform local-only side effects.** Broadcast and connection
   revocation are idempotent per instance and touch no shared state. Any future consumer that
   *writes* (audit, projection, email) must use a **shared** group (competing consumers), never a
   per-instance one — the `AdminDirective` KDoc restates this.

## Consequences

- Correctness: live updates and session revocation now reach clients on every pod without Redis
  or sticky routing; no rebalance storms (each group has exactly one member).
- Group proliferation: every pod leaves a consumer group whose committed offsets expire after the
  broker's offset-retention window. Harmless at template scale; noted for ops.
- The remaining multi-instance gap is the in-memory `TicketStore` (WebSocket handshake only, since
  ADR-0007): a ticket minted on pod A cannot redeem on pod B. Fix when WebSocket goes
  multi-instance: Redis-backed store (atomic compare-and-delete via `SETNX`/Lua) — or accept
  sticky routing for `/ws-ticket` + `/ws`. SSE is unaffected (bearer, stateless verification).
- Deployment note: rolling restarts drop live streams (each pod closes its connections on shutdown;
  clients reconnect and revalidate via `onConnect`). A preStop drain grace period makes this
  graceful rather than a visible blip.
