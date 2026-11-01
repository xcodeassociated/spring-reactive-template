# Spring Reactive MongoDB Template

A **service template** for quickly starting new backend applications — not an application itself. It ships a fully wired, production-shaped Spring Boot service with the boring parts already done: Keycloak authentication, MongoDB (replica set), Kafka, GraphQL, gRPC, WebSockets, observability, CI/CD, and Docker. The goal is to spend minutes, not days, turning an idea into a running, deployable service.

**Stack:** Kotlin · Spring Boot 4.1 · WebFlux + Coroutines · MongoDB · Kafka (KRaft) · Keycloak · Gradle 9 · Java 24

---

## Why this template

- **Prototype fast** — clone, `docker compose up`, and you have a secured CRUD service with auth, messaging, and a database.
- **Everything runs locally** — Keycloak, Kafka, and MongoDB come up via Docker Compose with pre-created topics and a configured event bridge.
- **Two idioms side by side** — most capabilities are demonstrated in *both* Reactor (`Flux`/`Mono`) and Kotlin coroutines (`Flow`/`suspend`), so you pick the style you like and delete the other.
- **Features are decoupled** — each optional feature lives in its own package/config with its own dependencies and properties. Removing one is a delete + a few lines in `build.gradle`, not surgery. See [Removing features](#removing-features) for the small number of known coupling points.

---

## Quick start

```bash
# 1. create bind-mount directories for compose volumes
./init_volumes.sh

# 2. start infrastructure: Kafka + Keycloak (+ Postgres) + MongoDB replica set
#    note: the Mongo container mounts ./mongo-rs/config/mongodb/mongod.conf,
#    which is not tracked in git — create it first (see Infrastructure)
docker compose up -d

# 3. configure the app
cp src/main/resources/config/env.template.properties src/main/resources/config/env.properties
# fill in Mongo / Kafka / OAuth / external-endpoint values

# 4. run
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

| Endpoint | URL |
|---|---|
| REST / WebFlux / GraphQL / SSE | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| GraphiQL | http://localhost:8080/graphiql |
| Actuator / Prometheus | http://localhost:8080/actuator |
| gRPC server (reflection enabled) | localhost:9080 |
| Keycloak (admin/admin) | http://localhost:8090 |
| Chat dev page (WebSocket + ticket auth) | http://localhost:8080/chat/chat.html (or serve `chat/` from :3000) |

Profiles: `local` (dev, OTLP export disabled), `docker` (used by the image), `integration` (test security bypass).

Helper requests for IntelliJ HTTP Client live in [`http/`](http) — copy `http-client.env.template.json` to `http-client.env.json` and fill in credentials. `http/auth.http` fetches Keycloak tokens; `http/keycloak.http`, `http/sample.http`, `http/ws.http` (WebSocket ticket mint + chat frames), etc. exercise the endpoints.

## Infrastructure (docker-compose.yml)

- **Kafka** — single-node KRaft broker; an init container creates `sample_topic_2`, `keycloak-events`, and `keycloak-admin-events` topics.
- **Keycloak** — `xcodeassociated/keycloak-kafka` image: standard Keycloak plus an event listener that publishes `REGISTER`/`LOGIN`/`LOGOUT` events to Kafka (the app consumes them in `KafkaKeycloakController`). Backed by Postgres.
- **MongoDB** — single-node replica set (`rs0`), matching the app's `spring.mongodb.replica-set-name` setting. The mounted `mongod.conf` is untracked, and the replica set must be initiated once (`rs.initiate()`) before it accepts writes.
- **tunnels/** — optional SSH tunnel helper for exposing local services during demos (`tunnels.template.conf` → `tunnels.conf`).

## Project structure

```
.
├── .github/workflows/
│   ├── ci.yml                    # build + tests on every branch / PR
│   └── build-and-upload.yml      # build, multi-arch Docker image push, dependency graph (Dependabot)
├── docker-compose.yml            # Kafka + Keycloak/Postgres + MongoDB replica set
├── Dockerfile                    # Temurin 24 JRE image (built jar)
├── docs/adr/                     # architecture decision records (immutable; supersede with a new ADR)
├── init_volumes.sh               # creates compose bind-mount directories
├── http/                         # IntelliJ HTTP client requests + env templates
├── tunnels/                      # SSH tunnel scripts/config (optional)
├── build.gradle                  # all feature dependencies, clearly grouped by comment
├── src/main/proto/               # gRPC protobuf schema (schema.proto)
├── src/main/resources/
│   ├── application*.properties   # default + local/docker/integration profiles
│   ├── config/env.properties     # local secrets/URLs (from template — keep out of git)
│   ├── graphql/schema.graphqls   # GraphQL schema
│   └── logback-spring.xml        # OTel log appender config
├── chat/chat.html                # static dev tool: browser chat over ticket-auth WebSocket (served at /chat/)
├── src/main/kotlin/com/softeno/template/
│   ├── SoftenoReactiveMongoApp.kt
│   ├── app/                      # ← reusable building blocks
│   │   ├── common/               # BaseDocument (id/version/audit), errors, principal utils
│   │   ├── config/               # SecurityConfig, Mongo auditing, error handling, OTel
│   │   ├── kafka/                # Kafka config, sample producer/consumer, Keycloak event listener
│   │   ├── permission/           # example domain: CRUD over REST, coroutines & GraphQL
│   │   └── user/                 # example domain + SSE update streams (notification/)
│   └── sample/                   # ← feature demos; delete together with their references in app/
│       ├── http/                 # WebFlux controller & functional router, external WebClient + circuit breaker
│       ├── proto/                # gRPC server service & client channel config
│       └── websocket/            # coroutine WebSocket handlers + one-time-ticket auth (api/config/security sub-packages)
└── src/test/kotlin/              # MockK unit tests, Testcontainers (Mongo/Kafka) & WireMock integration tests
```

Everything under `sample/` and the `user`/`permission` domains are **examples** — replace them with your own domain packages.

## Features

### Always-on core
- **WebFlux + Kotlin coroutines** — both styles configured (`reactor-kotlin-extensions`, `kotlinx-coroutines-reactor`), automatic context propagation.
- **Reactive MongoDB** — reactive repositories, `auto-index-creation`, optimistic locking (`version`), auditing (`createdBy`/`modifiedBy` from the JWT subject via `ReactiveMongoAuditorConfig`), `BaseDocument` common interface.
- **Keycloak / OAuth2 security** — JWT resource server, `realm_access` roles mapped to Spring authorities, method security, CORS config, per-path authorization rules in one file (`SecurityConfig.kt`).
- **Global error handling** — `WebExceptionHandler` + common `BaseException`/`ErrorFactory` pattern.
- **Testing** — JUnit 5, MockK/springmockk, `WebTestClient`, GraphQL test support, Testcontainers (Mongo + Kafka), WireMock for external HTTP, fixtures for test data.
- **Packaging & CI** — Gradle wrapper, CI build+test on every branch/PR, multi-arch (`amd64`/`arm64`) image pushed from `main`, Dependabot dependency graph.

### Optional (each independently removable)
- **Kafka** — JSON serializer/deserializer config, sample tx/rx topics, Keycloak auth-event consumer.
- **GraphQL** — spring-graphql controllers for both domains, GraphiQL enabled.
- **gRPC** — Kotlin coroutine stubs generated from `schema.proto`, server with reflection, named client channels.
- **WebSockets** — coroutine implementation (`com.softeno.ws.enabled`), with heartbeat, stale-session sweeper, one-time-ticket browser auth (§ [WebSocket chat auth](#websocket-chat-auth-one-time-ticket)), and token-expiry/logout close codes.
- **Server-Sent Events** — reactive and coroutine user-update emitters (`app/user/notification`).
- **Resilience4j circuit breaker** — wrapping an external `WebClient` call.
- **OpenAPI** — springdoc Swagger UI.
- **Observability** — Actuator + Prometheus, OpenTelemetry traces/metrics/logs over OTLP, trace-correlated logging.

## Removing features

The build file groups dependencies under comment headers (`// kafka`, `// graphql`, `// grpc`, …) that map 1:1 to feature packages and properties. To drop a feature:

| Feature | Delete | Also remove |
|---|---|---|
| Kafka | `app/kafka/` | `// kafka` deps, `spring.kafka.*` + `com.softeno.kafka.*` props, compose services |
| GraphQL | `*GraphqlController.kt` files | `// graphql` deps, `resources/graphql/`, `spring.graphql.*` props |
| gRPC | `sample/proto/`, `src/main/proto/` | `// grpc` deps + protobuf plugin in `build.gradle`, `spring.grpc.*` props |
| WebSockets | `sample/websocket/` and `chat/` | `com.softeno.ws.*` props (`com.softeno.ws.enabled` toggle), `/ws/**` + `/ws-ticket` + `/chat/**` security rules, references in `app/kafka/KafkaSampleHandler.kt` + `app/kafka/KafkaKeycloakController.kt` + `app/user/notification/` |
| SSE | `app/user/notification/` | `/update/**` security rule |
| Circuit breaker / external client | `sample/http/external/` | `// circuit breaker` dep, `com.softeno.external.*` props, references in `app/config/http/WebExceptionHandler.kt` + the `externalGraphQl` bean in `app/user/api/GraphqlController.kt` |
| OpenAPI | — | `// springdoc` deps, `springdoc.*` props |
| OpenTelemetry | `config/opentelemetry/` | otel deps, `management.opentelemetry.*` props, simplify `logback-spring.xml` |
| Security | `config/security/SecurityConfig.kt` (replace with permit-all) | `// security` deps, `spring.security.*` / OAuth props |
| Coroutines *or* Reactor flavor | the `reactive/` (or coroutine) halves of any package | — |

Apart from four coupling points listed above (`app/kafka/KafkaSampleHandler.kt`, `app/user/notification/Service.kt`, `app/config/http/WebExceptionHandler.kt`, `app/user/api/GraphqlController.kt` import from `sample/`), features don't import each other, so deletions don't cascade. Removing a feature's references, security rules, and properties is enough to keep the app compiling. (`app/kafka/KafkaKeycloakController.kt` also imports the WebSocket sender — it belongs to the same Kafka↔WebSockets coupling point as `KafkaSampleHandler.kt`.)

## WebSocket chat auth (one-time ticket)

Decision record with sequence diagrams and alternatives: [ADR-0001](docs/adr/0001-websocket-one-time-ticket-auth.md).

Browsers cannot send an `Authorization` header on a WebSocket upgrade (the `WebSocket`
constructor takes only a URL), so `/ws` authenticates with a **single-use ticket**
instead — never `?access_token=`, which RFC 9700 §4.3.2 forbids.

1. `POST /ws-ticket` with a normal bearer JWT → `{ "ticket": "...", "expiresIn": 10 }`.
   The ticket is 256-bit SecureRandom; the server stores only its **SHA-256** mapped to
   the mint-time claims (`sub`, `sid`, `exp`, roles) with a 10 s TTL.
2. `new WebSocket("ws://localhost:8080/ws?ticket=...")`. The ticket is redeemed
   **before the handshake completes** with an atomic compare-and-delete; the bound
   identity is put into the reactive security context for the handler.
3. Failures, pre-upgrade: **401** missing/expired/replayed ticket,
   **403** Origin not in `com.softeno.ws.allowed-origins` (CSWSH defense; CORS
   config does *not* protect WebSocket handshakes).

**Client contract** (wire format unchanged — JSON `{from, to, content}`):

| Event | Server sends | Client does |
|---|---|---|
| connect | `{"from":"SYSTEM","to":<sessionId>,"content":"HANDSHAKE"}`, then a frame whose content is your user id | start chatting; `to:"ALL"` broadcasts (echo includes sender) |
| heartbeat | `content:"ping"` every 10 s | reply `{"to":"SYSTEM","content":"pong"}` (`from` ignored) |
| token expired mid-session (sweeper) | `content:"REAUTH_REQUIRED"` then **close 4401** | refresh token, mint a **fresh** ticket, reconnect |
| logout (Keycloak event via Kafka) | `content:"LOGGED_OUT"` then **close 4403** | stop reconnecting, show logged-out UI |

Identity is bound at handshake from validated credentials: the server **stamps
`from`** with the session's bound `sub` on every relayed message; client-claimed
`from` is never trusted. Every reconnect attempt mints a fresh ticket.

Logout propagation uses the existing Keycloak→Kafka events
(`KafkaKeycloakController`) + the `userId → sessions` reverse index; the sweeper
enforces the token `exp` ceiling as the always-on backstop (Keycloak logout does
not revoke outstanding access tokens — keep them short). The registry, index,
and ticket store are per-process/per-instance.

Config (`application.properties`): `com.softeno.ws.ticket-ttl-seconds=10`,
`com.softeno.ws.allowed-origins=http://localhost:3000,http://localhost:8080`
(comma-separated exact match; `:8080` is the dev page's own origin),
`com.softeno.ws.sweep-interval-seconds=10`, plus the existing
`heartbeat-interval-seconds` / `stale-check*` keys.

**Chat page:** `chat/chat.html`, served at http://localhost:8080/chat/ (static,
permitAll) — keycloak-js from CDN → login → mint ticket → chat, two tabs see each
other. The page always calls the backend at `http://localhost:8080`, so it also
works when served from a static server (`(cd chat && python3 -m http.server 3000)`
→ http://localhost:3000/chat.html). Browser login requires the realm `app`
`frontend` client to list the page's origin as redirect/web origin — the stock
config allows `http://localhost:3000` (kcadm command for `:8080` in the file header).

**Try it without a browser:** `http/ws.http` (IntelliJ HTTP Client) mints tickets
and drives the socket with `WEBSOCKET` requests.

## Configuration reference

`application.properties` reads from `config/env.properties` (via `spring.config.import`), which is generated from `env.template.properties` and holds environment-specific values: Mongo connection, Kafka bootstrap/topics/group, OAuth issuer/client URIs and credentials, external service URLs. Secrets stay out of git; the Docker image expects the same variables at runtime.

## Architecture decision records

Significant design decisions are recorded in [`docs/adr/`](docs/adr):

| ADR | Decision | Status |
|---|---|---|
| [ADR-0001](docs/adr/0001-websocket-one-time-ticket-auth.md) | WebSocket chat auth via one-time tickets (+ session lifecycle: ceiling sweeper, logout revocation, 4401/4403 contract) | Accepted |

ADRs are **immutable** once accepted: when a decision or flow changes, do not rewrite
history — add a new numbered ADR explaining the new context and decision, and mark the
old one `Superseded by ADR-NNNN` (its status line is the only thing that may change).

## License

[MIT](LICENSE.md)
