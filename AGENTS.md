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
| Run app | `SPRING_PROFILES_ACTIVE=local ./gradlew bootRun` |
| Infra | `./init_volumes.sh && docker compose up -d` (Kafka KRaft, Keycloak+Postgres on :8090, MongoDB rs0 on :27017) |
| Config | `cp src/main/resources/config/env.template.properties src/main/resources/config/env.properties` and fill values — required, `application.properties` resolves `${...}` placeholders from it |

Requires JDK 24 (`sdkman` GraalVM works). Protobuf/gRPC stubs generate from `src/main/proto/schema.proto` via the protobuf Gradle plugin.

## Layout

- `src/main/kotlin/com/softeno/template/app/` — reusable blocks: `common/` (BaseDocument, errors, principal utils), `config/` (security, Mongo auditing, exception handling, OTel), `kafka/`, plus example domains `user/` and `permission/`.
- `src/main/kotlin/com/softeno/template/sample/` — removable feature demos: `http/` (`internal/` coroutine `SampleService` + `internal/coroutine/` controller + `internal/router/` functional endpoints; `external/` coroutine WebClient + circuit breaker), `proto/` (gRPC), `websocket/` (coroutine-only handlers with one-time-ticket browser auth: `api/WsTicketController.kt` mint (`suspend`), `security/` ticket store + redeem-at-upgrade filter + authentication rebuild, `config/WsChatToolConfig.kt` static chat-page serving, session registry with reverse index + sweeper at package root; shared model in `ChatCommon.kt`). Tests mirror the same sub-packages.
- `chat/chat.html` — static dev tool (vanilla JS + keycloak-js CDN) exercising the ticket-auth WebSocket; served at `/chat/` by `WsChatToolConfig` (the page itself always calls the backend at :8080, so it also works served from :3000, the origin the stock Keycloak `frontend` client allows).
- `docs/adr/` — architecture decision records; immutable once accepted, superseded by new numbered ADRs (see *Keeping docs current*).
- **Test layout**: integration tests end in `*IT.kt` and live in the package of the feature they test; unit tests stay `*Test.kt` (the suffix is a convention — Gradle's JUnit Platform runs both under `test`). Shared test infrastructure lives in `src/test/kotlin/.../testsupport/`: `BaseIntegrationTest` boots the app under profile `integration` (permit-all test `SecurityConfig`) with Testcontainers Kafka (`apache/kafka-native`) and Mongo (`mongo:6.0.4`), random ports, and wipes users/permissions after each test; `R` is the static random test-data utility — tests generate their data via `R` and assert against those values; `TestBearerWebFilter` simulates bearer auth via the `X-Test-Sub` header under the `integration` profile. Fixtures/abilities live in their context's package (e.g. `app/permission/PermissionFixture.kt`, `app/kafka/KeycloakEventFixture.kt`, `sample/http/external/ExternalApiAbility.kt`, `sample/websocket/WsTicketAuthAbility.kt` — the browser-like WS client harness). `sample/websocket/WsTicketAuthIT` is Testcontainers-free (random port + `X-Test-Sub` header auth). WireMock serves external HTTP on :4500.
- `http/` — IntelliJ HTTP client requests; copy `http-client.env.template.json` → `http-client.env.json` (gitignored). `ws.http` demos ticket mint + `WEBSOCKET` chat frames.
- `.github/workflows/` — `ci.yml` (build+test all branches), `build-and-upload.yml` (jar → multi-arch image `xcodeassociated/spring-reactive-template:latest` + dependency graph).

## Conventions

- **Coroutine-first** (no dual idioms): endpoints, services, and SSE/WS features are coroutine-based (`Flow`/`suspend`); the reactive Reactor API exists only as infrastructure beneath them (WebFlux transport, reactive Mongo repositories, `WebClient`, `WebFilter`/RouterFunction signatures — which the transport requires). Do not add parallel `reactive/` flavors of features.
- **Feature removal contract**: each optional feature = own package + a commented dependency group in `build.gradle` + its properties keys (`com.softeno.kafka.*`, `com.softeno.ws.*`, `spring.grpc.*`, …) + rules in `app/config/security/SecurityConfig.kt`. Known `app → sample` coupling points that must be removed together with their feature: `app/kafka/KafkaSampleHandler.kt` and `app/kafka/KafkaKeycloakController.kt` (websocket `Message`/emitters/`revokeUser`) and `app/user/notification/Service.kt` (websocket `Message`/emitters), `app/config/http/WebExceptionHandler.kt` and `app/user/api/GraphqlController.kt` (`externalGraphQl` bean, external client config). Avoid adding new cross-imports beyond these.
- Security rules live in the single main `SecurityConfig.kt` (path matchers, `realm_access` roles → authorities, method security via `@EnableMethodSecurity`); the test-profile config must keep paths permit-all. `/ws/**` and `/ws-ticket` are `.authenticated()` (WebSocket upgrade auth is the one-time ticket, not an `Authorization` header); `/chat/**` is permitAll static.
- Bean-level toggles use `@ConditionalOnProperty` (WebSockets: `com.softeno.ws.enabled=true`).
- Entities implement `app/common/BaseDocument.kt` (id, `@Version`, audited createdBy/Date); auditing pulls the JWT `sub` via `ReactiveMongoAuditorConfig`.
- Profiles: `local` (disables OTLP export), `docker` (set by Dockerfile entrypoint), `integration` (tests). Never commit secrets; only `env.template.properties` belongs in git.

## Gotchas

- `docker-compose.yml` bind-mounts `./mongo-rs/config/mongodb/mongod.conf`, which is not tracked in the repo and not created by `init_volumes.sh`; Mongo also needs its single-node replica set initiated before change streams work (unverified here).
- The Keycloak image `xcodeassociated/keycloak-kafka` is a third-party image that forwards auth events to Kafka topics consumed by `KafkaKeycloakController`.
- macOS ARM only: build conditionally adds `netty-resolver-dns-native-macos` (via `osdetector`).
- `HELP.md` is a stale Spring Initializr leftover (gitignored).

## Keeping docs current

- **`README.md` must always be up to date.** Before finishing any development task, check whether it changed anything the README documents (purpose, features, feature-removal table, project structure, commands, ports, setup steps, infrastructure) and update the README in the same change if so. Never hand off with a stale README.
- **`AGENTS.md` must always be up to date too.** Before finishing any development task, check whether it changed anything this file documents (stack versions, Gradle tasks/commands, feature set or its removal contract, security config structure, CI/deployment) and update it in the same change if so. Verify commands you document actually run; mark anything untested as such.
- **`docs/adr/` must stay accurate — but ADRs are immutable.** Never rewrite the context/decision of an accepted ADR to reflect new reality. When a documented decision or flow changes (e.g. the WebSocket ticket-auth flow in `ADR-0001`), write a new numbered ADR explaining the new context and decision, set the old ADR's status to `Superseded by ADR-NNNN` (the status line is the only editable part), and update the README ADR table plus any linked sections in the same change.
