# DoseRunner — Complete Project Guide

> Study sheet so you can answer any question about this codebase.
> **Snapshot:** branch `joint/day2-contracts`, commit `188dd05`, 2026-11-19.
> **Legend:** ✅ implemented and tested · 🟡 partly there / declared only · ⏳ planned only (written in docs, no code yet)
>
> Everything below marked **[verified]** I reproduced by running code (see §12). Everything else comes from reading the files.

---

## Table of contents
0. [The 60-second pitch](#0-the-60-second-pitch)
1. [Honest status: what exists vs what is only planned](#1-honest-status-what-exists-vs-what-is-only-planned)
2. [The business problem and the end-to-end flow](#2-the-business-problem-and-the-end-to-end-flow)
3. [Technology stack](#3-technology-stack)
4. [Architecture](#4-architecture)
5. [Code walkthrough (what is really written)](#5-code-walkthrough-what-is-really-written)
6. [The 12 ADR decisions in plain words](#6-the-12-adr-decisions-in-plain-words)
7. [Strengths (what to defend)](#7-strengths-what-to-defend)
8. [Problems and gaps](#8-problems-and-gaps)
9. [Questions your manager may ask, with answers](#9-questions-your-manager-may-ask-with-answers)
10. [Glossary](#10-glossary)
11. [Commands cheat-sheet](#11-commands-cheat-sheet)
12. [How this guide was produced](#12-how-this-guide-was-produced)

---

## 0. The 60-second pitch

**DoseRunner** is a hospital medication-logistics platform:

> A **nurse** orders a medication → a **pharmacist** validates it → an **autonomous robot** carries it to the ward →
> every handoff is **cryptographically signed** in a **tamper-evident custody ledger**.

It is a **Java 25 / Spring Boot 4.1 microservice system** (5 services + shared libraries) that talks through
**REST** (users), **gRPC** (order ↔ dispatch), and **ActiveMQ Artemis messages** (the order → dispatch → robot → custody
workflow). Messages are serialized with **Apache Avro**. Native code is called through the Java **FFM API** (libsodium for
Ed25519 signatures, a C A* path-finder). A **ROS/ROSJava** bridge drives a simulated turtle robot. **Istio** is the planned
service mesh.

It is a university project ("Programming Frameworks", IA3, ENISO) with **4 team members + 1 robotics person**. Each member owns
one "niche technology" (A = Istio, B = Avro, C = Artemis, D = FFM, R = ROSJava) — that explains many choices (e.g. why
Avro is used even for gRPC messages).

**Where we are:** end of **Day 2**. The foundation (build, shared library, CI, compose) and the **contracts** (Avro schemas,
queue names, REST table) are done. **The business services themselves are empty skeletons.** Know this before any meeting.

---

## 1. Honest status: what exists vs what is only planned

This is the most important table in the file. If your manager asks "does X work?", look here first.

| Area | Status | What is actually in the repo |
|---|---|---|
| Maven multi-module build, parent POM, Maven Wrapper | ✅ | 9 modules in the reactor, one version-management POM |
| `commons` (trace filter, error handler, `ApiError`, logback config) | ✅ | 6 classes (including the auto-configuration), **8 tests** |
| `contracts` (Avro schemas, queue names, JMS property names, Avro trust) | ✅ | 17 `.avsc` → 18 generated classes, 3 hand-written classes, **5 tests** |
| 5 Spring Boot services (identity, order, dispatch, custody, robot-sim) | 🟡 **skeletons** | `Application` class + `application.yml` + empty pyramid packages (`package-info.java` only) + 1 smoke test (2 checks). **No controller, no entity, no listener, no business logic.** |
| All endpoints in the README (29 REST, 2 gRPC, 7 Thymeleaf pages) | ⏳ | A design table only. Not one endpoint exists besides `/actuator/health` |
| Authentication / authorization (JWT RS256, roles, JWKS) | ⏳ | No `spring-security` dependency anywhere. `commons/sec/` is an empty package |
| Database (JPA, Flyway, Postgres driver) | ⏳ | Postgres runs in compose and the 4 DBs are created, but **no service has a JPA/JDBC dependency** |
| Artemis messaging | ⏳ | Broker runs with **stock config**. No JMS/Artemis client dependency in any service. `broker.xml`, `AvroMessageCodec`, `AvroMarshaller` not written |
| gRPC `FleetService` | ⏳ | Only the 4 Avro **message** schemas exist. No service descriptor, no marshaller, no gRPC dependency in `contracts` |
| Custody ledger, hash chain, Ed25519, `/verify` | ⏳ | Only the module skeleton. `commons-native` contains one `package-info.java` |
| `libroute.c` (A* in C) | ⏳ | `native/` contains only a README |
| `DispatchStrategy` SPI + v2 plugin (ServiceLoader) | ⏳ | `package-info.java` only |
| `robot-sim` | 🟡 | skeleton; not in docker-compose yet |
| `ros-bridge` | ⏳ | README only (separate Gradle / Java 11 build) |
| Istio, Kubernetes manifests | ⏳ | `deploy/istio/.gitkeep`, `deploy/k8s/.gitkeep` |
| docker-compose (Postgres, Artemis, 4 services) | ✅ | Works as a local stack; see bug in §8.1 |
| CI (GitHub Actions) | ✅ | `mvn verify` + `docker compose config` |
| ArchUnit rules that enforce the pyramid | ⏳ | Rules written in `CONTRIBUTING.md`, **not enforced by any test** |
| Contracts "frozen at tag `v0.1-contracts`" | 🟡 | **The tag does not exist.** ADR 0001 status is still `proposed` [verified: `git tag -l` is empty] |

**Git history:** 3 commits — initial commit, Day 1 skeleton, Day 2 contracts. One uncommitted change: `.gitignore` now
ignores `.claude` (local AI-tooling folder; not part of the project).

**Tests:** 23 in total (contracts 5, commons 8, smoke 10). All pass when built (see §12).

---

## 2. The business problem and the end-to-end flow

**Why it matters (domain):** in a hospital, medications — especially *controlled substances* (opioids) — must be traceable:
who handed what to whom, when. Today that is paper and trust. DoseRunner automates delivery with robots and makes every
handoff provable.

**Roles:** `NURSE`, `PHARMACIST`, `OPERATOR` (fleet), `AUDITOR`, `ADMIN`.

### The happy path (designed — events are the Avro schemas in `contracts`)

```
 NURSE            order-service           dispatch-service        robot            custody-service
   │ POST /orders      │                        │                    │                    │
   │──────────────────►│ reserve stock (local DB tx)                 │                    │
   │                   │──── OrderPlaced ───────────────────────────────────────────────►│ opens ledger
   │                   │
 PHARMACIST            │
   │ POST /pharmacy/orders/{id}/validate
   │──────────────────►│──── OrderValidated ───►│ strategy picks robot
   │                   │ + schedules            │ libroute (A*) computes path
   │                   │   DispatchTimedOut     │──── MissionCommand ─►│ drives
   │                   │   at T+120 s           │◄──── RobotTelemetry ─│ (x, y, battery)
   │                   │◄─── RobotAssigned ─────│                                           (order → ASSIGNED)
   │                   │──────────────────────────── RobotAssigned ────────────────────►│
   │ POST …/pickup     │
   │──────────────────►│──── PickupConfirmed ───────────────────────────────────────────►│ signs link #1 (pharmacy→robot)
 NURSE at the ward     │
   │ POST /orders/{id}/delivery
   │──────────────────►│──── DeliveryConfirmed ─────────────────────────────────────────►│ signs link #2 (robot→nurse)
                       │ status → COMPLETED
```

**Order status machine:** `PLACED → VALIDATED → ASSIGNED → PICKED_UP → COMPLETED`, plus `REJECTED` and `MANUAL_DELIVERY`.

### Failure paths (saga compensation)
| Situation | What happens |
|---|---|
| Pharmacist rejects | status `REJECTED`, reserved stock released |
| No robot assigned within **120 s** | `DispatchTimedOut` (an Artemis *scheduled* message) fires → order-service releases stock, status `MANUAL_DELIVERY`. If `RobotAssigned` arrived meanwhile, the timeout is ignored |
| Robot fault / low battery / path blocked / operator cancel | `MissionAborted` → custody signs an `ABORT` link → dispatch re-assigns (max **2** times) else `MANUAL_DELIVERY` |
| Not enough stock at order time | `POST /orders` → **409** |
| order-service cannot reach dispatch (gRPC) | circuit breaker fallback: UI shows "ETA unknown, queued" |

This is a **choreography-based saga**: no central orchestrator; each service reacts to events and emits new ones.

---

## 3. Technology stack

### 3.1 Already used in code (✅)
| Tech | Version | Where | Why |
|---|---|---|---|
| **Java** | 25 (`maven.compiler.release=25`) | whole build | Course requirement; needed later for the stable FFM API (Java 22+) |
| **Spring Boot** | 4.1.2 (Spring Framework 7.0.10, Tomcat 11.0.27, Hibernate Validator 9.1.5 — read from the test logs) | parent POM | Application framework. Boot 4 is modular: note `spring-boot-starter-webmvc` and packages like `org.springframework.boot.webmvc.error` |
| **Maven** + **Maven Wrapper** | 3.9.16 | `mvnw`, `.mvn/wrapper` | Reproducible build, nobody needs Maven installed |
| **Spring MVC** (servlet stack) | via Boot | `commons`, all services | REST controllers, filters |
| **Bean Validation** | Hibernate Validator | `commons` | `@Valid`, `@NotBlank` → 400 with field errors |
| **Spring Boot Actuator** | via Boot | all services | `/actuator/health` (+ liveness/readiness probes) for compose/k8s |
| **SLF4J + Logback** | via Boot | `logback-spring.xml` | Logging; **MDC** carries `traceId` |
| **logstash-logback-encoder** | 9.0 | `commons` | One JSON object per log line in the `prod` profile |
| **Apache Avro** + `avro-maven-plugin` | 1.12.2 | `contracts` | Schema-first serialization: `.avsc` → generated Java classes at build time |
| **JUnit 5, AssertJ, Mockito, MockMvc** | via `spring-boot-starter-test` | tests | Unit / web-layer / smoke tests |
| **Docker + Compose v2** | | `deploy/compose` | Local stack |
| **GitHub Actions** | `checkout@v4`, `setup-java@v4` (Temurin 25) | `.github/workflows/ci.yml` | CI |
| **PostgreSQL** | 16.16 | compose only | Database (one DB + one user per service) |
| **ActiveMQ Artemis** | 2.44.0 | compose only | Message broker |

### 3.2 Declared in the parent POM but **not used by any module yet** (🟡)
`grpc-bom 1.85.1`, `resilience4j-bom 2.4.0` (circuit breaker), `archunit-junit5 1.5.1` (architecture tests),
`bucket4j 8.22.0` (rate limiting), `jmh 1.37` (micro-benchmarks).
Reason: versions are centralised in one place *before* the code needs them (see CONTRIBUTING rule: "versions live only in the parent POM").

### 3.3 Planned (⏳) — named in README / CONTRIBUTING / ADR / package-info files
| Tech | Purpose |
|---|---|
| Spring Security (OAuth2 resource server), JWT **RS256**, **JWKS** | Authentication; identity-service signs tokens, others verify via the public key |
| Spring Data JPA + Flyway | Persistence + schema migrations (Flyway named in the POM comment) |
| Caffeine cache | `GET /formulary` cached, `PUT` evicts |
| Thymeleaf | MVC pages (`/login`, `/ui/orders/new`, …) |
| **gRPC** (`FleetService.GetEta`, `ReserveRobot`) with an **Avro marshaller** | order → dispatch synchronous calls |
| **Resilience4j** circuit breaker | wrap the gRPC calls |
| **Testcontainers** | integration tests with real Postgres/Artemis (PR template requires it "where there is I/O") |
| **FFM (Foreign Function & Memory API)** | call `libsodium` (Ed25519 sign, generichash) and `libroute.so` (A* in C, with an *upcall* asking Java whether a cell is blocked) |
| **ServiceLoader** | `dispatch-strategy-v2` plugin discovered at runtime (same idea as JDBC drivers) |
| **ROSJava / ROS Noetic / turtlesim** | physical-robot-style demo, isolated in its own Java 11 / Gradle build |
| **Istio**, Kubernetes | gateway routing by path prefix, mTLS, canary of strategy v2 (week 3) |
| Artemis **HA** (live/backup pair), DLQ, redelivery policy | Resilience of the messaging layer (week 2) |
| JMH | benchmark native vs Java path-finding |

---

## 4. Architecture

### 4.1 Service map

| Module | Owner / niche | Port | DB | Responsibility |
|---|---|---|---|---|
| `contracts` | B (Avro) | — | — | The **only thing shared between services**: Avro schemas + names of queues/properties |
| `commons` | joint | — | — | Cross-cutting library: tracing, error format, logging |
| `commons-native` | D (FFM) | — | — | FFM bindings (planned) |
| `identity-service` | A (Istio) | 8081 | `identity` | Users, roles, login, JWT + JWKS |
| `order-service` | B (Avro) | 8082 | `orders` | Orders, pharmacist validation, stock, formulary, saga state |
| `dispatch-service` | C (Artemis) | 8083 (+gRPC 9090) | `fleet` | Fleet, missions, `DispatchStrategy` SPI, gRPC server |
| `dispatch-strategy-v2` | C | — | — | Load-aware strategy plugin |
| `custody-service` | D (FFM) | 8084 | `custody` | Signed hash-chained ledger + `/verify` |
| `robot-sim` | C | 8085 | — | Pure-Java fake robot (fallback to ROS) |
| `ros-bridge` | R | — | — | ROSJava node; **not** in the Maven reactor |
| `native/` | D | — | — | C sources (`libroute.c`) |
| `deploy/` | A (C for artemis) | — | — | compose, k8s, istio, artemis |

**Key microservice rules visible in the repo**
- **Database per service** (4 databases, 4 users, `REVOKE ALL ON DATABASE … FROM PUBLIC`).
- **Services never share entities** — only the `contracts` jar (ADR "Consequences").
- Services are independently deployable: one Dockerfile builds any module via `--build-arg MODULE=…`.

### 4.2 The "Pyramid" (layering inside every service)

```
  pres/     @RestController, @Controller (Thymeleaf), gRPC + Artemis *adapters*     ← may call facade ONLY
    │
  facade/   the only entry point for pres. @Transactional lives here and nowhere else
    │
  bo/       Business Objects: pure business rules. No Spring Web, no JPA
    │
  dao/      Spring Data JPA repositories + @Entity classes
  config/   Spring configuration of the service

  commons/ = the "transversal layer" crossing all levels: dto, sec, trace, util
```
Rules (CONTRIBUTING.md): `pres` never imports `dao`; `bo` never imports `org.springframework.web`; `@Transactional` only in
`facade`; constructor injection only. **They are announced "enforced by an ArchUnit test, week 1" — that test does not exist yet.**

This is the classic Java-EE-era **layered / n-tier** architecture (Presentation → Session Facade → Business Object → DAO),
not hexagonal / clean architecture. See §8.3 for the consequences.

### 4.3 Three communication styles

| Style | Used for | Technology | Status |
|---|---|---|---|
| **Synchronous request/response, external** | Browser/UI/API clients → services | REST + JSON, JWT bearer | ⏳ (only the error/trace plumbing exists) |
| **Synchronous, internal** | order → dispatch: "what is the ETA?" and "reserve a robot" | gRPC with Avro payloads | ⏳ (schemas only) |
| **Asynchronous events/commands** | the whole saga, robot commands, telemetry | Artemis + Avro | ⏳ (names + schemas only) |

### 4.4 Messaging model (Artemis) — **locked** in `Addresses.java` / `deploy/artemis/README.md`

Two routing types you must be able to explain:
- **ANYCAST** = point-to-point: one message goes to *one* consumer (like a queue).
- **MULTICAST** = publish/subscribe: each subscribed *queue* gets its own copy.

| Address | Routing | Queues | Payload |
|---|---|---|---|
| `doserunner.events` | multicast | `order-service`, `dispatch-service`, `custody-service` (one per consumer) | all 7 events |
| `robot.commands` | anycast | `robot.commands` | `MissionCommand` |
| `robot.telemetry` | multicast | `dispatch-service` | `RobotTelemetry` |
| `DLQ` | anycast | `DLQ` | messages that failed **3** deliveries |
| `ExpiryQueue` | anycast | `ExpiryQueue` | expired messages |

- A consumer subscribes to a multicast queue with the **FQQN** `address::queue`, e.g. `doserunner.events::dispatch-service`.
- **Message properties on every message:** `eventType` (record simple name, for selectors), `traceId`, `orderId`.
  On `robot.commands`: `robotId` + `JMSXGroupID`. For `DispatchTimedOut`: `_AMQ_SCHED_DELIVERY` (Artemis scheduled delivery, epoch ms).
- **Robot addressing (ADR #7):** every command carries `robotId` and `JMSXGroupID=robotId`; each robot consumes with the JMS
  selector `robotId='<own id>'`. The **selector routes** to the right robot; the **message group keeps order** per robot.

### 4.5 Wire format (ADR #11)
Body is a JMS `BytesMessage` containing **Avro Single-Object Encoding**:

```
 C3 01 │ 8-byte CRC-64-AVRO fingerprint of the WRITER schema (little-endian) │ Avro binary payload
 marker│                                                                      │
```
The reader looks the schema up by fingerprint. An unknown fingerprint throws `MissingSchemaException` — it fails loudly instead
of misreading bytes. The test `decoderRejectsBytesWrittenWithAnotherSchema` proves this.

### 4.6 Observability / cross-cutting (✅ implemented in `commons`)

```
 request ─► CorrelationIdFilter (HIGHEST_PRECEDENCE: before Spring Security, so even a 401 has a trace id)
              │  traceparent (W3C) → x-request-id → generated UUID (32 hex)
              │  → MDC["traceId"], request attribute "traceId", response header x-request-id
              ▼
          controller ─► exception? ─► GlobalExceptionHandler ─► ApiError JSON {…, traceId}
              ▼ (error never reaches a controller: 404, 401/403, filter failure)
          container /error ─► Boot BasicErrorController ─► TraceIdErrorAttributes (adds traceId to Boot's default body)
```
Logs: readable text by default `HH:mm:ss LEVEL [service,traceId] logger - msg`; **JSON when the `prod` profile is active**
(compose sets it). In JSON, every MDC key becomes a field, plus `"service"`.

### 4.7 Deployment & CI
- **`deploy/compose/docker-compose.yml`**: `postgres:16.16`, `apache/activemq-artemis:2.44.0`, 4 services built from one
  `Dockerfile`. YAML anchors (`x-service`, `x-service-env`) avoid repetition; `depends_on … condition: service_healthy`;
  healthchecks hit `/actuator/health/readiness`.
- **Dockerfile** (multi-stage): `maven:3.9.16-eclipse-temurin-25` builds with `-pl ${MODULE} -am`, a BuildKit cache mount keeps
  `~/.m2`; runtime is `eclipse-temurin:25.0.5_8-jre`, **non-root user**, `-XX:MaxRAMPercentage=75`, `curl` installed for healthchecks.
- **`init-databases.sh`**: creates DB + user per service, runs once on an empty volume.
- **CI**: checkout → Java 25 Temurin with Maven cache → `./mvnw -B -ntp verify` → `docker compose config -q`.
  Concurrency group cancels superseded runs; 20-minute timeout.
- **Branching** (`CONTRIBUTING.md`): `main` protected, 1 approval from a non-author, CI green; branch prefixes per owner;
  rotating reviewer (A→B→C→D→A); `contracts/` and `commons/` changes need **all 4** approvals; Conventional Commits; PR template.
- **Definition of Done:** code + unit test + integration test (where there is I/O) + logs with traceId + `NicheIntegration.md` updated + PR merged.

---

## 5. Code walkthrough (what is really written)

### 5.1 Parent POM — [pom.xml](pom.xml)
- Parent is `spring-boot-starter-parent:4.1.2` → inherits the Boot BOM (Spring, Jackson, Hibernate, Artemis client, Testcontainers, JUnit… versions).
- `groupId tn.rnu.eniso.doserunner`, `artifactId doserunner-parent`, `0.1.0-SNAPSHOT`, `packaging pom`.
- **Rule written in the file:** every dependency/plugin version lives here; children declare **no** `<version>`.
- `<dependencyManagement>`: the internal modules, the gRPC and Resilience4j **BOMs** (imported), single artifacts (avro, logstash encoder, bucket4j, archunit, jmh).
- `pluginManagement`: pins `avro-maven-plugin`.
- `ros-bridge` is deliberately outside the reactor (rosjava does not build on Java 25).

### 5.2 `commons` — [commons/](commons/)

| File | What it does | Why it is written that way |
|---|---|---|
| [CommonsWebAutoConfiguration](commons/src/main/java/tn/rnu/eniso/doserunner/commons/config/CommonsWebAutoConfiguration.java) | Registers 3 beans: the filter, the exception handler, the error attributes. Listed in `META-INF/spring/…AutoConfiguration.imports` | **Auto-configuration** instead of component scan: a service just adds the dependency, nothing else. `@ConditionalOnMissingBean` lets a service override each bean. `@ConditionalOnWebApplication(SERVLET)`. `beforeName=ErrorMvcAutoConfiguration` so our `ErrorAttributes` is registered before Boot's default, which then backs off |
| [CorrelationIdFilter](commons/src/main/java/tn/rnu/eniso/doserunner/commons/trace/CorrelationIdFilter.java) | Extends `OncePerRequestFilter`. Resolves the trace id (see §4.6), writes MDC + request attribute + response header, **removes MDC in `finally`** | Request attribute because the MDC is already cleared when the container does the `/error` dispatch. Regexes make it safe: `traceparent` must match W3C format (lowercase hex, non-zero trace-id); a client `x-request-id` is accepted only if `^[A-Za-z0-9._-]{1,64}$` → **blocks log/header injection** (tested with `"evil\r\nX-Injected: 1"`) |
| [TraceHeaders](commons/src/main/java/tn/rnu/eniso/doserunner/commons/trace/TraceHeaders.java) | Constants: `traceparent`, `x-request-id`, MDC key `traceId`, `JMS_TRACE_ID` | One place for the names, shared by HTTP/gRPC/Artemis paths |
| [GlobalExceptionHandler](commons/src/main/java/tn/rnu/eniso/doserunner/commons/web/GlobalExceptionHandler.java) | `@RestControllerAdvice(annotations = RestController.class)`. `MethodArgumentNotValid` & `ConstraintViolation` → 400 with `fieldErrors`; `IllegalArgumentException` → 400; any `ErrorResponse` (e.g. `ResponseStatusException`) keeps its status; everything else → **500 "Unexpected error"** (logged with stack trace, **not** leaked); Spring-Security exceptions are re-thrown so Security produces 401/403 | Single error contract. Restricted to `@RestController` so Thymeleaf pages are unaffected. The `org.springframework.security.` **name check** exists because `commons` must not depend on spring-security |
| [TraceIdErrorAttributes](commons/src/main/java/tn/rnu/eniso/doserunner/commons/web/TraceIdErrorAttributes.java) | Extends Boot's `DefaultErrorAttributes`, adds `traceId` from the request attribute | Covers errors that never reach a controller |
| [ApiError](commons/src/main/java/tn/rnu/eniso/doserunner/commons/dto/ApiError.java) | `record(timestamp, status, error, message, path, traceId, fieldErrors)` + nested `FieldError(field, message)`. Compact constructor makes `fieldErrors` an immutable non-null list | Java record = immutable DTO |
| [logback-spring.xml](commons/src/main/resources/logback-spring.xml) | `prod` profile → `LogstashEncoder` JSON (+ `service` field, shortened stack traces, root-cause first). Other profiles → text pattern with `%X{traceId}`. `tn.rnu.eniso.doserunner` at **DEBUG**, root at INFO | Lives in the `commons` jar so every service picks it up automatically |
| `sec/` and `util/` packages | **Empty** (`package-info.java`) | Reserved for A (JWT/rate-limit) |

`commons` depends on: `spring-boot-starter-webmvc`, `spring-boot-starter-validation`, `logstash-logback-encoder`.

### 5.3 `contracts` — [contracts/](contracts/)

**Build:** `avro-maven-plugin` runs the `schema` goal in `generate-sources`; imports `common/` first so events can reference
`Location`, `GridPoint`, etc.; `stringType=String` (generated getters return `java.lang.String`, not `CharSequence`).
Output goes to `target/generated-sources/avro` (not committed).

**Hand-written Java (3 classes, no logic besides trust):**
- [AvroTrust](contracts/src/main/java/tn/rnu/eniso/doserunner/contracts/AvroTrust.java) — Avro 1.12 refuses to instantiate classes
  that are not on a trusted list (anti-deserialization-gadget protection). `install()` is idempotent and **composes** with
  any existing global predicate, trusting only `tn.rnu.eniso.doserunner.contracts.*`. Alternative:
  `-Dorg.apache.avro.SERIALIZABLE_PACKAGES=…`.
- [Addresses](contracts/src/main/java/tn/rnu/eniso/doserunner/contracts/messaging/Addresses.java) — Artemis address/queue names + `fqqn()`.
- [MessageHeaders](contracts/src/main/java/tn/rnu/eniso/doserunner/contracts/messaging/MessageHeaders.java) — JMS property names.

**The 17 schemas** (generated package in brackets):

| Group | Schema | Fields (short) | Producer → Consumers |
|---|---|---|---|
| common | `GridPoint` | `x`, `y` (ints) | path cell for the A* grid |
| common | `Location` | `ward`, `room?` (nullable), `point: GridPoint` | |
| common | `OrderRef`, `RobotRef` | `orderId` / `robotId` | wrappers used in gRPC messages |
| events | `OrderPlaced` | `eventId, occurredAt, orderId, patientRef, drugCode, qty, wardLocation, controlled, nurseId` | order → custody |
| events | `OrderValidated` | `…, pharmacistId, destination, controlled` | order → dispatch |
| events | `RobotAssigned` | `…, robotId, missionId, etaSeconds, path[GridPoint]` | dispatch → order, custody |
| events | `PickupConfirmed` | `…, robotId, pharmacistId` | order → custody (link #1), dispatch |
| events | `DeliveryConfirmed` | `…, robotId, nurseId` | order → custody (link #2), dispatch |
| events | `DispatchTimedOut` | `eventId, occurredAt, orderId` | order → itself (scheduled) |
| events | `MissionAborted` | `…, robotId, missionId, reason: AbortReason(enum), detail?` | dispatch → order, custody, dispatch |
| robot | `MissionCommand` | `missionId, robotId, orderId, seq, waypoints[], compartmentCode, issuedAt` | dispatch → robots |
| robot | `RobotTelemetry` **v1** | `robotId, x, y, battery` | robots → dispatch |
| grpc | `EtaRequest/Reply`, `ReserveRequest/Reply` | uses `OrderRef`, `RobotRef`, `Location`; `holdSeconds` default 120; optionals are `["null", T]` with `default: null` | order ↔ dispatch |

Every event starts with `eventId` (UUID, the **dedup key**), `occurredAt` (`timestamp-millis`), `orderId` (saga key).
`AbortReason` is an enum **with a default (`UNKNOWN`)** so adding values later stays backward-compatible.

### 5.4 The five service skeletons
Identical structure (they differ only by name, port, description):
- `pom.xml`: depends on `commons`, `contracts`, `spring-boot-starter-actuator` (+ test starter); `spring-boot-maven-plugin` builds the executable jar.
- `*Application.java`: standard `@SpringBootApplication` + `main`.
- `application.yml`: `spring.application.name`, `server.port: ${SERVER_PORT:80xx}`, actuator exposes `health,info`, health **probes** enabled.
- `bo/ dao/ facade/ pres/ config/` — **only `package-info.java`** files documenting each layer's rule (`robot-sim` has no `dao`).
- One smoke test (`@SpringBootTest` on a random port): (1) `/actuator/health` → 200 `UP` and `x-request-id` echoed; (2) unknown path → 404 with `traceId` in body.

### 5.5 Tests that exist (23)
| Class | # | Proves |
|---|---|---|
| `ContractsRoundTripTest` | 5 | All 13 record samples survive encode→decode; bytes start with `C3 01` + the schema fingerprint; decoder rejects another schema's bytes; defaults (`holdSeconds=120`, null `room`/`detail`) apply; every schema is under the contracts namespace |
| `CorrelationIdFilterTest` | 5 | traceparent wins; invalid traceparent / all-zero → falls back; unsafe request-id rejected; id generated if none; MDC set during request, echoed, **cleaned afterwards** |
| `GlobalExceptionHandlerTest` | 3 | validation → 400 with `fieldErrors` + traceId; `ResponseStatusException` keeps 404; unexpected exception → 500 without leaking the message |
| 5 × `…SmokeTest` | 10 | Each service boots; health is UP; trace id echoed; 404 body has trace id |

---

## 6. The 12 ADR decisions in plain words
Source: [docs/adr/0001-contracts-v0.1.md](docs/adr/0001-contracts-v0.1.md) (status: **proposed**).

1. **Every event has `eventId` + `occurredAt` + `orderId`.** Artemis is *at-least-once* (a message may be redelivered), so consumers must be idempotent; `eventId` is the dedup key.
2. **`OrderValidated` carries `destination` + `controlled`.** So dispatch never has to call order-service back or keep a copy of orders (stays stateless w.r.t. orders).
3. **`OrderPlaced.nurseId` added.** custody needs to know who ordered; order-service checks it at delivery.
4. **`GridPoint` split from `Location`.** A path cell has no ward/room; repeating them on every cell would bloat messages.
5. **`MissionAborted.reason` is an enum with a default.** New reasons are a compatible change.
6. **`MissionCommand.seq`.** Per-robot sequence number to *prove* "in order, no duplicates" in the HA demo.
7. **Robot addressing = selector + message group.** Groups alone stick to whichever consumer got them first; the selector does the routing, the group keeps the order.
8. **`RobotTelemetry` frozen at v1.** v2 (`status` enum, nullable `temperatureC`) comes in week 2 as the schema-evolution demo + CI compatibility job.
9. **gRPC messages use `OrderRef`/`RobotRef`**; optionals are nullable unions with `default: null`; `ReserveRobot` is idempotent on `orderId`; `holdSeconds` defaults to 120.
10. **Avro class trust.** `AvroTrust.install()` trusts only our contracts package.
11. **Wire format** = `BytesMessage` + Avro single-object encoding; one test checks marker, fingerprint, and round-trip.
12. **Custody has no write endpoint.** Ledger links are created only from events → no HTTP caller can forge a link.

---

## 7. Strengths (what to defend)

**Process & structure**
- **Contract-first**: schemas, queue names, header names and the REST table were agreed *before* code, and every deviation from the blueprint is recorded with its reason in an ADR.
- **Single source of truth for versions** (parent POM) + a PR-template checkbox to keep it that way.
- **Clear ownership** (owner per module and per branch prefix), rotating reviewer, protected `main`, Conventional Commits, CI on every PR.
- **Reproducible**: Maven Wrapper, Docker multi-stage build, compose with health-gated startup, compose file validated in CI.

**Engineering quality in the code that exists**
- `commons` is a proper **Spring Boot auto-configuration** (conditions, back-off, ordering) — not a copy/paste of beans into each service.
- **Trace id from the first request**, defensive parsing (regex, W3C zero-id rule), MDC cleaned in `finally`, request-attribute trick for the `/error` dispatch.
- Error handler **does not leak internals on 500** (there is a test with `"db password is hunter2"`).
- **Security thinking in the design**: custody has no write API; Avro class allow-list; patient reference is pseudonymised (never a name); DB per service with least-privilege users; non-root containers; secrets/keys gitignored.
- **Idempotency and ordering are designed in**: `eventId`, idempotent `ReserveRobot`, per-robot `seq`, message groups.
- **Compensation is designed** (timeout + abort/re-assign) instead of assuming a happy path.
- **Schema evolution is planned** (enum default, nullable-with-default fields, v2 telemetry demo).
- Wire format is verified by a **fingerprint test**, and decoding foreign bytes fails loudly.
- `ApiError` record is immutable (defensive copy in the compact constructor).

---

## 8. Problems and gaps

Severity: 🔴 will bite soon / contradicts a claim · 🟠 real design risk · 🟡 clean-code / polish.

### 8.1 Verified defects (I reproduced these)

| # | Sev | Problem | Evidence & fix |
|---|---|---|---|
| 1 | 🔴 | **Malformed JSON body and wrong-typed path variable return HTTP 500 "Unexpected error"** (and log an ERROR with stack trace) instead of 400. | [verified] `POST /p` with body `{not json` → **500**; `GET /p/abc` with `Long id` → **500**. Cause: `HttpMessageNotReadableException` and `MethodArgumentTypeMismatchException` are **not** `ErrorResponse`, so they fall into `onUnexpected`'s catch-all. Fix: extend `ResponseEntityExceptionHandler` (handles all standard MVC exceptions) or add explicit 400 handlers. Test names claim "unexpected… 500" but only `IllegalStateException` is tested. |
| 2 | 🟠 | **`IllegalArgumentException` is mapped to 400 and its raw message is returned to the client.** Any internal `IAE` (library, or a bug) becomes a "client error" and may leak internals. | [verified] `throw new IllegalArgumentException("internal detail: column ORDERS.PATIENT_REF is null")` → **400** with that exact text in `message`. The 500 path is carefully non-leaking; this path is not. Fix: use dedicated domain exceptions (below) and drop this handler. |
| 3 | 🔴 | **README says "every REST error is an `ApiError` JSON body" — false.** Errors that bypass the advice (unknown path, wrong method with no handler, Security 401/403) use Boot's default body. | [verified] A booted service returns `404 → {"timestamp","status","error","path","traceId"}` — **no `message`, no `fieldErrors`**. Two shapes coexist. Smoke test only asserts `traceId` is present, so it never noticed. Fix: one shape — adopt RFC 9457 `ProblemDetail` for both paths, or make `TraceIdErrorAttributes` emit the `ApiError` fields. |
| 4 | 🔴 | **Changing a DB password in `.env` breaks the stack.** | [verified via `docker compose config`] `IDENTITY_DB_PASSWORD=s3cret` → the **service** gets `s3cret`, but the **postgres container never receives that variable**, so `init-databases.sh` still creates the user with the default `identity`. Same for orders/fleet/custody. README tells people to override via `.env`. Fix: pass the 4 `*_DB_PASSWORD` variables into the `postgres` service `environment`. |
| 5 | 🟠 | **`tn.rnu.eniso.doserunner` is logged at DEBUG in every profile, including `prod`.** | `logback-spring.xml`: the `<logger … level="DEBUG"/>` is outside the `<springProfile>` blocks. In a medical app, DEBUG in production risks logging patient/order data and floods logs. Fix: DEBUG only in `!prod`. |
| 6 | 🟠 | **`contracts` will be compiled for Java 25, but `ros-bridge` is Java 11.** | `maven.compiler.release=25` is inherited by `contracts` (class-file version 69; I saw 65 only because I overrode it). `MissionCommand.avsc` even says "also read by the Java 11 ros-bridge: keep it to plain types" — but the build does not enforce `release 11`. A Java 11 JVM cannot load those classes. Fix: set `maven.compiler.release` to 11 in `contracts/pom.xml` (Avro-generated code is Java-8 compatible), or have ros-bridge generate its own classes from the `.avsc`. |
| 7 | 🟠 | **The project cannot be built with the JDK that is on this machine.** | `java -version` here is **21**; the project requires **25** (README, CI, Dockerfile). `./mvnw verify` fails at compile. With `-Dmaven.compiler.release=21` in a scratch copy the **whole build + 23 tests pass**, so nothing uses Java 22+ APIs yet. Fix: install JDK 25; add Maven Enforcer (`requireJavaVersion`) for a friendly message. |

### 8.2 Documentation vs reality (be careful what you claim)

| Claim in the docs | Reality |
|---|---|
| README: contracts "frozen at `v0.1-contracts`" | The git tag **does not exist**; ADR 0001 is `proposed` |
| README: `contracts` = "Avro schemas + gRPC descriptors" | Only Avro schemas for the **messages**. No `FleetService` descriptor, no marshaller, no gRPC dependency in `contracts/pom.xml` |
| README: "Every service follows the same Pyramid" | Only empty packages with Javadoc; no ArchUnit test |
| README/Definition of Done: `docs/NicheIntegration-*.md` | No such files exist (`docs/` has only `adr/` and `journal/` placeholders) |
| ADR / schemas: "Blueprint §7 / §11" | The blueprint is not in the repo |
| README lists 29 REST endpoints, 2 gRPC methods and 7 Thymeleaf pages | None implemented. It is a *target design* |
| Compose passes `SPRING_DATASOURCE_*` and `SPRING_ARTEMIS_*` and waits for Postgres + Artemis | No service has JPA/JMS dependencies, so these settings do nothing yet |
| `deploy/artemis/README.md`: "`broker.xml` must match" | `broker.xml` does not exist; the broker runs on stock settings |

### 8.3 Architecture and design risks (not built yet — questions you must be ready for)

1. **Dual-write problem (no outbox).** "`OrderPlaced` is published after stock was reserved in the same local transaction" — but a DB commit and an Artemis send are two separate resources. If the commit succeeds and the send fails (or the reverse), the saga silently diverges. Nothing in the design mentions a **transactional outbox**, XA, or a JMS-transacted strategy. Same on the consumer side: dedup by `eventId` requires an **inbox/processed-events table** that is not designed yet.
2. **Event fan-out.** All 7 event types go to one multicast address, so **each of the 3 queues receives every event** (e.g. `DispatchTimedOut`, meant for order-service only, is also copied to custody and dispatch). There is an `eventType` property for selectors, but no queue filters are defined (`broker.xml` is missing). Options: filtered queues, or one address per event.
3. **Selector + message group on one shared anycast queue (`robot.commands`).** More moving parts than **one queue/address per robot** (e.g. `robot.commands.R-01`). A command for a robot that is offline stays in the shared queue; selectors on a shared queue are a classic source of "stuck message" confusion.
4. **Avro single-object encoding without a schema registry.** Decoders know only the schemas compiled into their jar. The generated `fromByteBuffer` accepts **only its own fingerprint** (the test proves it). So the planned "schema evolution" (telemetry v2) and rolling upgrades only work once `AvroMessageCodec` is written with a **SchemaStore containing every historical version**. Today any schema change = decoding failure for old consumers.
5. **Avro for gRPC is unconventional.** gRPC normally means Protobuf. Avro payloads work (custom marshaller) but lose `grpcurl`/reflection/tooling and are non-idiomatic. The honest reason is the *niche technology* requirement (B owns Avro).
6. **Pyramid contradictions.**
   - `bo` is "no JPA" but sits *above* `dao` (which holds `@Entity`): if `bo` calls `dao`, it transitively depends on JPA types. Either `bo` works on plain domain objects mapped by `facade`, or the rule must be relaxed.
   - **Dependency direction points to the database** (domain depends on persistence). Hexagonal/clean architecture inverts this.
   - **Outbound adapters have no home**: `pres` hosts *inbound* gRPC/Artemis adapters, but where do the Artemis **publisher** and the gRPC **client** live? Not defined.
   - **No domain exceptions** in `commons` (e.g. `NotFoundException`, `ConflictException` for the documented 404/409). Without them `bo` can only signal HTTP-ish errors via `ResponseStatusException` — which is in `org.springframework.web.server` and **violates the "bo never imports spring web" rule**.
7. **Home-grown tracing.** `CorrelationIdFilter` + constants re-implement part of **Micrometer Tracing / OpenTelemetry**: only the trace id is kept (no span id, no parent), and propagation to outgoing HTTP/gRPC/JMS calls is not written. Istio/Envoy will emit `traceparent`, so reading it is good, but a custom filter will need to grow.
8. **Custom `ApiError` vs RFC 9457 `ProblemDetail`.** Spring has a built-in standard; a bespoke DTO reinvents it and is the root of defects #1–#3.
9. **Custody trust model (planned, think ahead).**
   - Tamper-evidence only holds if the signing key and the latest hash are **outside** an attacker's reach; the ledger and the service live in the same Postgres/process. An insider with DB + key can rewrite and re-sign a chain.
   - Chains are **per order** (`seq`, `prevHash`): **truncating the tail** or deleting a whole order's chain is undetectable without an external anchor of the head hash.
   - Events are **not signed by the actor**. `pharmacistId` in `PickupConfirmed` is *asserted by order-service*. The ledger proves "order-service said so, and nobody changed it since", not "the pharmacist did it".
   - **Anyone with Artemis credentials can publish a forged event** that custody will sign; compose uses one shared `artemis/artemis` account for all services. "No HTTP caller can forge a link" is true, "no one can" is not. Broker-level per-address permissions are needed.
10. **Security is entirely absent today.** No Spring Security → every endpoint (just actuator) is open; no TLS; Istio mTLS is week 3. Future gotcha: with Spring Security 6+, the `/error` dispatch is also secured — it must be permitted, otherwise Security's own 401/403 error pages become 401 again.
11. **`commons` pulls the servlet web stack + validation into every consumer**, even a module that only needs `TraceHeaders`.
12. **Sync + async mixed, no timeout/retry policy written yet** for gRPC (`Resilience4j` declared, unused) — the "ETA unknown, queued" fallback only exists as a sentence in a schema doc.

### 8.4 Clean-code and maintainability

| Sev | Issue |
|---|---|
| 🟡 | **Copy-paste ×5:** the 5 service `pom.xml`, `application.yml`, `*Application.java` and smoke tests are identical except names (I diffed them — the smoke tests are byte-identical after renaming). Common config (`management.*`, probes) belongs once in `commons`; the smoke test belongs in a shared test base / `commons-test` module. |
| 🟡 | **Duplicated constant:** `"traceId"` exists in `commons` `TraceHeaders.JMS_TRACE_ID` **and** in `contracts` `MessageHeaders.TRACE_ID` (and the MDC key is also `"traceId"`). Two sources of truth that must stay equal by luck. |
| 🟡 | **String-based type check** `ex.getClass().getName().startsWith("org.springframework.security.")` — brittle; use `@ConditionalOnClass` configuration or an advice ordered after Security's. |
| 🟡 | **Index-coupled tests:** `samples().get(1)`, `.get(8)`, `.get(6)` in `ContractsRoundTripTest` break silently if someone reorders the list. Use a map keyed by name. |
| 🟡 | **Speculative / unused build config:** grpc, resilience4j, bucket4j, archunit, jmh managed but unused; `enableDecimalLogicalType=true` although no schema uses decimals. |
| 🟡 | **Profile abuse:** the compose stack (a *dev* stack) activates profile `prod` just to get JSON logs. A neutral profile (`json-logs`) would stop "prod" meaning two things. |
| 🟡 | **Hard-coded Artemis string** `"_AMQ_SCHED_DELIVERY"` copied instead of using the client's constant (fine for a client-free contracts module, but it is a magic string). |
| 🟡 | Smoke tests read the port with `@Value("${local.server.port}")` and hand-roll `HttpClient` instead of the test client Boot provides. |
| 🟡 | `GlobalExceptionHandler.onUnexpected` declares `throws Exception` to rethrow — a smell that signals the catch-all is doing two jobs. |
| 🟡 | Documentation refers to missing artefacts (blueprint, NicheIntegration files) — a newcomer cannot follow them. |

### 8.5 Security, DevOps and process gaps

| Sev | Issue |
|---|---|
| 🟠 | CI workflow has **no `permissions:` block** (GITHUB_TOKEN gets default scope; should be `contents: read`). Actions pinned to tags, not commit SHAs. |
| 🟠 | CI does **not** build the Docker images, run a linter/formatter (Spotless/Checkstyle), measure coverage (JaCoCo), or scan dependencies (Dependabot/OWASP/Trivy). |
| 🟠 | Architecture rules are **unenforced** (no ArchUnit); no Maven Enforcer (Java version, dependency convergence, banned versions in children). The PR-template checkbox "no version in child pom" is manual. |
| 🟠 | No integration tests with real infrastructure (Testcontainers is mentioned but not present); every test today is unit or in-memory. |
| 🟡 | Compose exposes Postgres `5432` and the Artemis console `8161` on the host with default credentials (`artemis/artemis`, `postgres/postgres`); fine for dev, but must not leave the laptop. |
| 🟡 | `init-databases.sh` interpolates passwords into SQL (`PASSWORD '${pass}'`) — a quote in a password breaks it (SQL injection by env var; dev-only). |
| 🟡 | No Artemis **volume** → broker data is lost on `compose down`; no resource limits; no `server.shutdown: graceful`. |
| 🟡 | Dockerfile: `COPY . .` before the build invalidates the layer on any file change (the cache mount softens it); runtime image installs `curl` only for healthchecks; Maven version pinned both in the wrapper and in the image tag. |
| 🟡 | Actuator is open and exposes `health,info`. Fine now; must be reviewed when Security arrives (and a `prometheus` endpoint is needed for the planned observability). |

### 8.6 If I had one afternoon — suggested order
1. Fix compose password propagation (#4) — 3-line change.
2. Scope the DEBUG logger to non-prod (#5).
3. Replace the exception handler base with `ResponseEntityExceptionHandler` + `ProblemDetail`, add `NotFoundException`/`ConflictException`, drop the `IllegalArgumentException` handler, and make `/error` use the same shape (#1–#3, §8.3-6, -8). Add tests for malformed JSON and type mismatch.
4. Set `release 11` on `contracts` (#6) or decide how ros-bridge gets its classes.
5. Add the `v0.1-contracts` tag **only after** all four approve the ADR, then change its status to *accepted*.
6. Add `permissions: contents: read` to CI; add ArchUnit test; add Maven Enforcer.
7. Write the outbox/inbox decision as ADR 0002 **before** coding the messaging.

---

## 9. Questions your manager may ask, with answers

**Q1. In one sentence, what does the system do?**
It lets a nurse order a medication, a pharmacist validate it, a robot deliver it, and records every handoff in a signed, hash-chained ledger so it can be audited.

**Q2. What is actually finished today?**
The foundation and the contracts: a 9-module Maven build, a shared `commons` library (trace id, uniform errors, JSON logging), 17 Avro schemas with generated classes and the locked Artemis names, docker-compose, CI, and 23 passing tests. The five services are skeletons that boot and answer `/actuator/health`; the business endpoints are not implemented yet.

**Q3. Why microservices for a project this size?**
Honest answer: the course asks us to integrate distributed technologies (Istio, Artemis, gRPC, FFM, ROS), which only make sense with several services. The domain also has clearly separate bounded contexts (identity, orders, fleet, custody) and different data and trust needs (custody must be isolated and append-only). The cost is distributed complexity (sagas, idempotency, observability) — which is why contracts and tracing were done first.

**Q4. Why Avro and not JSON or Protobuf?**
Compact binary, a **schema is the contract**, generated typed classes, defined evolution rules (defaults, nullable unions), and a verifiable fingerprint in the wire format. It is also B's niche technology. Trade-off: no schema registry, so every consumer needs the historical schemas in its jar (see §8.3-4); and Avro for gRPC is unconventional.

**Q5. How do you avoid breaking services when a message changes?**
The contracts jar is the only shared artifact; changes after the tag need approval of all four members; schema fields use defaults/nullable unions and enums have defaults; a CI compatibility check is planned for week 2. *Be honest:* today the generated decoder only understands its own schema, so the evolution mechanism (a codec with a schema store) is not written yet.

**Q6. Artemis delivers at-least-once. What about duplicates?**
Every event has a unique `eventId`; consumers must record processed ids and ignore repeats; `ReserveRobot` is idempotent on `orderId`; commands carry a per-robot `seq`. *Not designed yet:* the inbox table and, more importantly, how publishing is made atomic with the DB commit (outbox) — I would raise this as the next ADR.

**Q7. What if the robot never gets assigned?**
At validation time order-service schedules a `DispatchTimedOut` message for T+120 s (Artemis scheduled delivery). If no `RobotAssigned` was seen, it releases the stock and marks the order `MANUAL_DELIVERY`. If a robot was assigned, the timeout is ignored.

**Q8. Why a selector *and* a message group on `robot.commands`?**
A message group makes all messages with the same `JMSXGroupID` go to the same consumer **in order**, but it sticks to whichever consumer got the first message, so it can't pick the right robot. The selector `robotId='R-01'` routes to the right robot. Together: right robot, strict order. (A queue-per-robot would be simpler; be ready to discuss it.)

**Q9. How is the custody ledger tamper-evident?**
Each link contains the hash of the previous link and is signed with Ed25519 (libsodium through FFM). `/verify` recomputes the chain and checks signatures; the public key is exposed for offline verification; there is no write endpoint so links come only from events. *Limits to admit:* per-order chains can be truncated unless the head hash is anchored externally, and events themselves are not signed by the human actor. (Not implemented yet.)

**Q10. How do you trace a request across services?**
`CorrelationIdFilter` takes the W3C `traceparent` (Istio sets it) or `x-request-id` or generates an id, puts it in the MDC so every log line has it, echoes it in the response, and includes it in every error body. For Artemis the id travels as the `traceId` message property. *Not yet:* propagation on outgoing calls, and spans (a move to Micrometer Tracing/OpenTelemetry would be natural).

**Q11. How are errors handled?**
A `@RestControllerAdvice` maps validation errors to 400 with per-field details, keeps `ResponseStatusException` statuses, and turns anything unexpected into a 500 that does not leak details. *Known gaps (verified):* malformed JSON and bad path-variable types currently give 500, and 404/401/403 use Boot's default body rather than `ApiError`. Fix = `ResponseEntityExceptionHandler` + `ProblemDetail`.

**Q12. Why is the shared library an auto-configuration?**
So a service only adds the dependency — no component scanning, no copy-pasted beans — and each bean backs off if a service defines its own (`@ConditionalOnMissingBean`). It is ordered before Boot's error auto-config so our `ErrorAttributes` wins.

**Q13. What is the "Pyramid"?**
The layering in every service: `pres → facade → bo → dao` with a transversal `commons`. Pres exposes REST/gRPC/messaging adapters, facade is the single transactional entry point, bo holds business rules, dao holds JPA. *Weak points:* `bo` vs JPA ambiguity, domain depends on persistence, outbound adapters undefined, rules not yet enforced by ArchUnit.

**Q14. Why Spring Boot 4 and Java 25?**
Course requirement (current LTS-era stack, and Java 22+ is needed for the stable FFM API we use for libsodium/libroute). Boot 4 brings the modular starters (`starter-webmvc`) and Spring Framework 7.

**Q15. How do you manage dependency versions?**
Only in the parent POM, on top of the Boot BOM; children declare no versions. A PR-template checkbox reminds reviewers. (An Enforcer rule would make it automatic.)

**Q16. How do you run it?**
`./mvnw verify` for build and tests (JDK 25), `docker compose -f deploy/compose/docker-compose.yml up --build -d` for Postgres + Artemis + 4 services, health at `http://localhost:808x/actuator/health`, Artemis console at `:8161`.

**Q17. What are the biggest risks?**
(1) Messaging consistency: outbox/inbox not designed; (2) schema evolution without a registry; (3) security is entirely absent today; (4) a large amount of business code remains while the foundation is done; (5) toolchain: JDK 25 required, and `contracts` must be consumable by the Java 11 `ros-bridge`.

**Q18. How is quality enforced?**
Protected `main`, one non-author approval, CI (`mvn verify`), Conventional Commits, PR template, Definition of Done. Gaps: no coverage, lint, dependency scan, or ArchUnit yet.

**Q19. Why one Postgres with four databases?**
Database-per-service isolation (own user, no `PUBLIC` access) while keeping local dev light. In production each service would get its own instance/cluster; a single instance is a shared point of failure.

**Q20. What would you do next?**
Day 3+: security (identity + JWT in `commons/sec`), JPA + Flyway, the Artemis `broker.xml` and `AvroMessageCodec`, the order flow end-to-end, then FFM/custody and dispatch; plus the fixes in §8.6.

**If you do not know an answer:** say whether it is *designed*, *implemented* or *not decided yet*. Using §1 and §8 you can always give an honest and precise status.

---

## 10. Glossary

| Term | Meaning |
|---|---|
| **Saga** | A business transaction made of local transactions linked by events, with compensating actions on failure instead of a distributed lock |
| **Choreography** | Saga style where services react to each other's events (no central orchestrator) |
| **Idempotent** | Doing it twice has the same effect as once; needed because delivery is at-least-once |
| **At-least-once** | The broker may deliver a message more than once, but never loses it |
| **Outbox pattern** | Write the event to a DB table in the *same* transaction as the data; a relay publishes it afterwards → no dual write |
| **Avro** | Schema-based binary serialization; schema in `.avsc` JSON |
| **Single-Object Encoding** | Avro framing: marker `C3 01` + 8-byte schema fingerprint + payload |
| **Fingerprint (CRC-64-AVRO)** | 64-bit hash of the canonical writer schema; used to find the schema at decode time |
| **Union `["null", T]`** | Avro way to say "optional"; `default: null` makes it backward-compatible |
| **Artemis / ActiveMQ Artemis** | Apache JMS/AMQP broker |
| **Address / Queue** | In Artemis an *address* is the destination name; *queues* are bound to it |
| **Anycast / Multicast** | Point-to-point vs publish-subscribe routing |
| **FQQN** | Fully Qualified Queue Name `address::queue` |
| **JMS selector** | SQL-like filter on message properties, e.g. `robotId='R-01'` |
| **JMSXGroupID / message group** | All messages with the same id go to the same consumer, in order |
| **DLQ** | Dead-Letter Queue: messages that exceeded max delivery attempts |
| **Scheduled message** | Artemis delays delivery until a given time (`_AMQ_SCHED_DELIVERY`) |
| **MDC** | SLF4J *Mapped Diagnostic Context*: thread-local key/values added to every log line |
| **W3C Trace Context / `traceparent`** | Standard header `version-traceid-spanid-flags` |
| **ProblemDetail (RFC 9457)** | Standard JSON error format built into Spring |
| **Auto-configuration** | Spring Boot mechanism: beans registered conditionally from a library via `AutoConfiguration.imports` |
| **BOM** | Bill of Materials: a POM that pins compatible versions |
| **Reactor** | Maven's multi-module build |
| **Actuator probes** | `/actuator/health/liveness` and `/readiness` for container orchestrators |
| **FFM API** | Java's Foreign Function & Memory API (final in Java 22): call C libraries without JNI |
| **Upcall** | C code calling back into Java (libroute asking "is this cell blocked?") |
| **libsodium / Ed25519** | Crypto library / fast elliptic-curve signature scheme |
| **A\*** | Shortest-path search on a grid |
| **ServiceLoader** | JDK plugin discovery via `META-INF/services` |
| **JWT RS256 / JWKS** | Signed token / endpoint publishing the public key to verify it |
| **Istio** | Service mesh (Envoy sidecars): routing, mTLS, canary, tracing headers |
| **ROS / ROSJava / turtlesim** | Robot Operating System / Java client / simple 2-D simulator |
| **ADR** | Architecture Decision Record |
| **Pyramid (this project)** | `pres → facade → bo → dao` layering + transversal `commons` |
| **Controlled substance** | Regulated drug (opioids…): custody signatures are mandatory |

---

## 11. Commands cheat-sheet

```bash
# Build + all tests (needs JDK 25; Windows: mvnw.cmd)
./mvnw verify

# One module and what it depends on
./mvnw -pl order-service -am verify

# Regenerate Avro classes only
./mvnw -pl contracts install

# Local stack (Postgres, Artemis, identity/order/dispatch/custody)
docker compose -f deploy/compose/docker-compose.yml up --build -d
curl http://localhost:8082/actuator/health
docker compose -f deploy/compose/docker-compose.yml logs -f order-service   # JSON logs with traceId

# Validate the compose file (what CI does)
docker compose -f deploy/compose/docker-compose.yml config -q

# Build one image by hand
docker build --build-arg MODULE=order-service -f deploy/compose/Dockerfile .
```
URLs: services `8081–8084` · Artemis console `http://localhost:8161` (`artemis`/`artemis`) · Postgres `localhost:5432`.

---

## 12. How this guide was produced

- I **read the project files** (all poms, all hand-written Java sources, all 17 schemas, compose, Dockerfile, init script, CI, READMEs, ADR, CONTRIBUTING). The five service modules were compared by `diff` against `order-service` (they differ only by names/ports/descriptions). I did not read the generated Maven-wrapper scripts (`mvnw`, `mvnw.cmd`).
- In a **scratch copy** of `HEAD` (your working tree was not touched) I ran the full build with the Java target lowered from 25 to 21 (your machine has JDK 21): **BUILD SUCCESS, 23/23 tests pass**.
- In that scratch copy I added **throw-away probe tests** to confirm §8.1 #1–#3 (error handler behaviour with malformed JSON, type mismatch, `IllegalArgumentException`, and the real 404/405 body of a booted service).
- I ran `docker compose config` with an overridden `IDENTITY_DB_PASSWORD` to confirm §8.1 #4.
- **Not done:** I did not start the docker stack, and I could not use JDK 25. Anything about Artemis/Postgres/Istio/FFM/ROS runtime behaviour is from the design documents, not from running it. The sections on custody and messaging risks (§8.3) are **design analysis**, not observed failures — those components do not exist yet.
