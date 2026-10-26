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
- `src/main/kotlin/com/softeno/template/sample/` — removable feature demos: `http/` (controller, functional router, external WebClient + circuit breaker), `proto/` (gRPC), `websocket/` (coroutine & reactive handlers).
- `src/test/kotlin/.../IntegrationTest.kt` — `BaseIntegrationTest` boots the app under profile `integration` (permit-all test `SecurityConfig`) with Testcontainers Kafka (`apache/kafka-native`) and Mongo (`mongo:6.0.4`), random ports, WireMock for external HTTP, MockK/springmockk, fixtures in `fixture/`.
- `http/` — IntelliJ HTTP client requests; copy `http-client.env.template.json` → `http-client.env.json` (gitignored).
- `.github/workflows/` — `ci.yml` (build+test all branches), `build-and-upload.yml` (jar → multi-arch image `xcodeassociated/spring-reactive-template:latest` + dependency graph).

## Conventions

- **Dual idioms**: capabilities exist in both Reactor (`Flux/Mono`, `reactive/` packages) and coroutines (`Flow/suspend`) variants. When adding a feature, follow the existing pair or note which style it is.
- **Feature removal contract**: each optional feature = own package + a commented dependency group in `build.gradle` + its properties keys (`com.softeno.kafka.*`, `com.softeno.ws.*`, `spring.grpc.*`, …) + rules in `app/config/security/SecurityConfig.kt`. Known `app → sample` coupling points that must be removed together with their feature: `app/kafka/KafkaSampleHandler.kt` and `app/user/notification/Service.kt` (websocket `Message`/emitters), `app/config/http/WebExceptionHandler.kt` and `app/user/api/GraphqlController.kt` (`externalGraphQl` bean, external client config). Avoid adding new cross-imports beyond these.
- Security rules live in the single main `SecurityConfig.kt` (path matchers, `realm_access` roles → authorities, method security via `@EnableMethodSecurity`); the test-profile config must keep paths permit-all.
- Bean-level toggles use `@ConditionalOnProperty` (e.g. `com.softeno.ws.type=coroutine|reactive`).
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
