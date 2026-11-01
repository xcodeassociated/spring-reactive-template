# AGENTS.md

Guidance for coding agents working in this repository. Human-facing docs: `README.md`.

## Purpose

This is a **service template**, not a product. It provides a pre-wired Spring Boot reactive service (auth, DB, messaging, API styles, observability, CI) so new apps can be prototyped by cloning and deleting unwanted features. Keep features decoupled and removable when adding anything new.

## Stack

Kotlin 2.4 · Spring Boot 4.1 (WebFlux) · Java 24 toolchain · Gradle wrapper 9.3.1 · MongoDB (reactive) · Kafka · Keycloak/OAuth2 · spring-graphql · spring-grpc + grpc-kotlin · coroutines (`kotlinx-coroutines-reactor`, `reactor-kotlin-extensions`).

## Commands

| Task | Command |
|---|---|
| Compile | `./gradlew classes` (verified) |
| Build + tests | `./gradlew build` (what CI runs; needs Docker for Testcontainers) |
| Tests only | `./gradlew test` |
| Coverage | `./gradlew koverHtmlReport` → `build/reports/kover/html` (generated gRPC stubs excluded) |
| Run app | `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` |
| Infra | `./init_volumes.sh && docker compose up -d` (Kafka KRaft, Keycloak+Postgres on :8090, MongoDB rs0 on :27017) |
| Config | `cp src/main/resources/config/env.template.properties src/main/resources/config/env.properties` and fill values — required, `application.properties` resolves `${...}` placeholders from it |

Requires JDK 24 (`sdkman` GraalVM works). Protobuf/gRPC stubs generate from `src/main/proto/schema.proto` via the protobuf Gradle plugin.

## Layout

- `src/main/kotlin/com/softeno/template/` — three feature packages + infrastructure (ADR-0003):
  - `users/` — **application core**: `UserService` works on db documents (no domain wrapper classes; permissions are embedded `PermissionSnapshot`s kept fresh by `UserPermissionSync` fan-out on permission update/delete — ADR-0005), `api/` REST+GraphQL, `db/` documents+repositories, `permission/` sub-domain; the write path ENDS at `UserEventsPublisher` (Kafka tx). Core never imports notifications/sample.
  - `notifications/` — **optional feature** (deleting it keeps the app compiling): `Notification.kt` port (`broadcast` + `revokeUser`, ADR-0004), `Message.kt` envelope, `ticket/` shared one-time-ticket infra (`TicketStore`, `TicketRecord`/`TicketOrigin`, `TicketAuthentication`, `TicketProperties`), `ws/` adapter (handlers, session registry + reverse index + sweeper, `api/WsTicketController.kt` mint, `security/` redeem filter + `SessionBinding`, toggled by `com.softeno.ws.enabled`), `sse/` adapter (`Service.kt` emitter, `SseSubscriptionRegistry` reverse index + ceiling sweeper, `api/SseTicketController.kt` mint, `security/SseTicketRedeemFilter.kt`), `kafka/` listeners (user events → `List<Notification>` broadcast; Keycloak LOGOUT → `revokeUser` on every adapter).
  - `sample/` — removable demos: `http/` (`internal/` coroutine service/controller/router; `external/` WebClient + circuit breaker), `proto/` (gRPC). The WebSocket demo moved to `notifications/ws/`.
  - `common/` (BaseDocument, errors, paging utils) + `config/` (security, Mongo auditing, exception handling, OTel, kafka wiring incl. `WebToolConfig` static page serving) — infrastructure, imports nothing from feature packages (except `config/kafka` wiring the core's `UserCreatedEvent`; `config/graphql` holds the global GraphQL exception handler).
- Repo-root `web/` — static dev tools served at `/web/**` by `config/http/WebToolConfig.kt` (permitAll): `chat.html` (ticket-auth WebSocket) and `sse.html` (ticket-auth SSE lifecycle — re-mint on error, `token_expired` reconnect, `logged_out` stop; doubles as the reference frontend implementation). The pages always call the backend at :8080, so they also work served from :3000, the origin the stock Keycloak `frontend` client allows.
- `docs/adr/` — architecture decision records; immutable once accepted, superseded by new numbered ADRs (see *Keeping docs current*).
- **Test layout**: integration tests end in `*IT.kt` and live in the package of the feature they test; unit tests stay `*Test.kt` (the suffix is a convention — Gradle's JUnit Platform runs both under `test`). Shared test infrastructure lives in `src/test/kotlin/.../testsupport/`: `BaseIntegrationTest` boots the app under profile `integration` (permit-all test `SecurityConfig`) with Testcontainers Kafka (`apache/kafka-native`) and Mongo (`mongo:6.0.4`), random ports, and wipes users/permissions after each test; `R` is the static random test-data utility — tests generate their data via `R` and assert against those values; `TestBearerWebFilter` simulates bearer auth via the `X-Test-Sub` header under the `integration` profile. Fixtures/abilities live in their context's package (e.g. `users/permission/PermissionFixture.kt`, `notifications/kafka/KeycloakEventFixture.kt`, `sample/http/external/ExternalApiAbility.kt`, `notifications/ws/WsTicketAuthAbility.kt` — the browser-like WS client harness). `notifications/ws/WsTicketAuthIT` is Testcontainers-free (random port + `X-Test-Sub` header auth). WireMock serves external HTTP on :4500.
- `http/` — IntelliJ HTTP client requests; copy `http-client.env.template.json` → `http-client.env.json` (gitignored). `ws.http` demos ticket mint + `WEBSOCKET` chat frames.
- `.github/workflows/` — `ci.yml` (build+test all branches), `build-and-upload.yml` (jar → multi-arch image `xcodeassociated/spring-reactive-template:latest` + dependency graph).

## Conventions

- **Coroutine-first** (no dual idioms): endpoints, services, and SSE/WS features are coroutine-based (`Flow`/`suspend`); the reactive Reactor API exists only as infrastructure beneath them (WebFlux transport, reactive Mongo repositories, `WebClient`, `WebFilter`/RouterFunction signatures — which the transport requires). Do not add parallel `reactive/` flavors of features.
- **Feature removal contract**: each optional feature = own package + a commented dependency group in `build.gradle` + its properties keys (`com.softeno.kafka.*`, `com.softeno.ws.*`, `com.softeno.ticket.*`, `com.softeno.sse.*`, `spring.grpc.*`, …) + rules in `config/security/SecurityConfig.kt`. The core→notifications boundary is Kafka (ADR-0003) and the listeners drive transports only through the `Notification` port (ADR-0004) — so there are **no `app → sample` coupling points left**. Remaining known couplings: `config/http/WebExceptionHandler.kt` (external client config — the `externalGraphQl` bean now lives inside `sample/` itself) and one `userEventsPublisher.publishUserCreated` call in `UserService` (delete with Kafka). `notifications/ticket/` is shared WS+SSE infra — remove only after both adapters.
- Security rules live in the single main `SecurityConfig.kt` (path matchers, `realm_access` roles → authorities, method security via `@EnableMethodSecurity`); the test-profile config must keep paths permit-all. `/ws/**` and `/update/**` are `.authenticated()` via header **or** one-time ticket (redeemed by `WsTicketRedeemFilter` at the upgrade / `SseTicketRedeemFilter` at the stream GET — browsers cannot send headers; tickets are transport-bound via `TicketOrigin`); `/ws-ticket` + `/sse-ticket` mint endpoints and `/sample-secured/**` + `/minio/**` are `.authenticated()`; `/web/**` is permitAll static (dev-tool pages).
- Bean-level toggles use `@ConditionalOnProperty` (WebSocket adapter: `com.softeno.ws.enabled=true` — the adapter then simply isn't in the `List<Notification>` fan-out).
- Entities implement `common/BaseDocument.kt` (id, `@Version`, audited createdBy/Date); auditing pulls the JWT `sub` via `ReactiveMongoAuditorConfig`. Services work on db documents — no domain wrapper classes (ADR-0003).
- Profiles: `local` (disables OTLP export), `docker` (set by Dockerfile entrypoint), `integration` (tests). Never commit secrets; only `env.template.properties` belongs in git.

## Gotchas

- `docker-compose.yml` bind-mounts `./mongo-rs/config/mongodb/mongod.conf`, which is not tracked in the repo and not created by `init_volumes.sh`; Mongo also needs its single-node replica set initiated before change streams work (unverified here).
- The Keycloak image `xcodeassociated/keycloak-kafka` is a third-party image that forwards auth events to Kafka topics consumed by `KafkaKeycloakController`.
- macOS ARM only: build conditionally adds `netty-resolver-dns-native-macos` (via `osdetector`).
- `HELP.md` is a stale Spring Initializr leftover (gitignored).
- Coroutine `saveAll` does not apply version-checked updates to existing documents (verified, Spring Data Mongo 5.1) — `UserPermissionSync` saves per entity; keep it that way.
- Embedded document shapes have no migration framework: after changing `UserDocument`'s nested types, wipe the dev collection (e.g. `mongosh db.userDocument.deleteMany({})`) or old rows 500 on read. Same trap on the dev Kafka topic: `sample_topic_2` may hold pre-typed-refactor payloads (`{"content":"USER_CREATED: …"}`) — a *fresh* consumer group replays them and retry-burns (committed groups are unaffected; wipe the topic or accept the skips).

## Keeping docs current

- **`README.md` must always be up to date.** Before finishing any development task, check whether it changed anything the README documents (purpose, features, feature-removal table, project structure, commands, ports, setup steps, infrastructure) and update the README in the same change if so. Never hand off with a stale README.
- **`AGENTS.md` must always be up to date too.** Before finishing any development task, check whether it changed anything this file documents (stack versions, Gradle tasks/commands, feature set or its removal contract, security config structure, CI/deployment) and update it in the same change if so. Verify commands you document actually run; mark anything untested as such.
- **`docs/adr/` must stay accurate — but ADRs are immutable.** Never rewrite the context/decision of an accepted ADR to reflect new reality. When a documented decision or flow changes (e.g. the WebSocket ticket-auth flow in `ADR-0001`), write a new numbered ADR explaining the new context and decision, set the old ADR's status to `Superseded by ADR-NNNN` (the status line is the only editable part), and update the README ADR table plus any linked sections in the same change.
