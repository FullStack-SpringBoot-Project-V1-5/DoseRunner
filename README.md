# 💊 DoseRunner

Hospital medication logistics: a nurse orders a medication, a pharmacist validates it, an autonomous robot
carries it to the ward, and every handoff is signed in a tamper-evident custody ledger.

Programming Frameworks project (IA3) · Spring Boot 4.1 · Java 25 · Istio · Avro · ActiveMQ Artemis · FFM · ROSJava

## Modules

| Module                 | Owner | What                                                                 | Port |
|------------------------|-------|----------------------------------------------------------------------|------|
| `contracts`            | B     | Avro schemas + gRPC descriptors (frozen, change = PR approved by 4)  | —    |
| `commons`              | joint | Pyramid transversal layer: dto, sec, trace, util, `logback-spring.xml` | —    |
| `test-support`         | joint | `TestJwt`: sign test tokens, test 401/403 without identity (test scope) | —    |
| `commons-native`       | D     | FFM bindings: libsodium, libroute                                    | —    |
| `identity-service`     | A     | users, roles, JWT RS256 + JWKS                                       | 8081 |
| `order-service`        | B     | orders, validation, stock, formulary, saga state                     | 8082 |
| `dispatch-service`     | C     | fleet, missions, `DispatchStrategy` SPI, gRPC server                 | 8083 |
| `dispatch-strategy-v2` | C     | load-aware strategy plugin (ServiceLoader)                           | —    |
| `custody-service`      | D     | signed hash-chained ledger + `/verify`                               | 8084 |
| `robot-sim`            | C     | pure-Java fallback robot                                             | 8085 |
| `ros-bridge`           | R     | ROSJava node + turtlesim (separate Gradle/Java 11 build)             | —    |
| `native/`              | D     | `libroute.c` (A* in C)                                               | —    |
| `deploy/`              | A (C: artemis) | compose, k8s, istio, artemis                                | —    |

Every service follows the same Pyramid: `pres/ → facade/ → bo/ → dao/` (+ `config/`).

## Prerequisites
- JDK 25 (`java -version`) — Maven is **not** required, use the wrapper `./mvnw`
- Docker with Compose v2

## Build & test
```bash
./mvnw verify            # Windows: mvnw.cmd verify
```

## Run the local stack
```bash
sh deploy/compose/gen-jwt-key.sh          # once: identity-service signing key (gitignored, never commit it)
docker compose -f deploy/compose/docker-compose.yml up --build -d
curl http://localhost:8082/actuator/health      # {"status":"UP",...}
docker compose -f deploy/compose/docker-compose.yml logs -f order-service   # JSON logs with traceId
sh deploy/compose/week1-gate.sh            # end-to-end check: login → order → UI validation → Artemis → robot moves
```
| What            | URL / port                                   |
|-----------------|----------------------------------------------|
| Postgres        | `localhost:5432` — DBs `identity`, `orders`, `fleet`, `custody` (user = db name, dev password = db name) |
| Artemis console | http://localhost:8161 (`artemis` / `artemis`) |
| Services        | 8081–8084                                    |

Dev credentials are defaults only; override them with env vars or a local `.env` (gitignored).

## Tracing & errors (from `commons`, auto-configured)
- `CorrelationIdFilter`: trace id from `traceparent` → `x-request-id` → generated; put in the MDC as `traceId`,
  echoed in the `x-request-id` response header.
- Logs: readable text by default, JSON when the `prod` profile is active (compose sets it).
- Errors: every REST error is an `ApiError` JSON body carrying the `traceId`.

## Security (from `commons/sec`, auto-configured)
- **Opt in** by adding `spring-boot-starter-security-oauth2-resource-server` to your service POM. Compose already sets
  `DOSERUNNER_SECURITY_JWKSURI`; for a local run set `doserunner.security.jwks-uri: http://localhost:8081/.well-known/jwks.json`.
- You then get: RS256 validation against identity's JWKS + issuer check, `roles` claim → `ROLE_*`
  (`@PreAuthorize("hasRole('NURSE')")` works, method security is already on), a stateless chain, and 401/403 as `ApiError`.
- Public without a token: `/actuator/health/**`, `/actuator/info`, `/error`, `/.well-known/**`, plus your
  `doserunner.security.public-paths`.
- **Enabled in (I2):** identity, order, dispatch, custody. robot-sim has no HTTP API. The JWT `sub` (user id) is the
  actor: order-service stores it as `nurseId` / `pharmacistId`, and `X-User-Id` can no longer override it.
- ⚠ **Thymeleaf pages of order, dispatch and custody (`/ui/**`) are public for now** (no login there yet, they use demo
  actor ids). Only identity's pages have a form login. Team decision pending for the other UIs.
- **Tests:** add `test-support` (scope `test`), `@Import(TestJwtConfiguration.class)`, then
  `.header("Authorization", TestJwt.bearer("NURSE"))`. Also `TestJwt.expired(...)`. No identity-service needed.
- **Rate limit** (every service, no opt-in needed): a token bucket per user (JWT `sub`) or per IP when anonymous.
  The default is 200 burst + 200/min. Over the limit you get **429** + `Retry-After` + `ApiError`. Configure it with
  `doserunner.ratelimit.{capacity,refill-per-minute,enabled}`.

### Dev users (seeded by identity-service Flyway `V2`, dev only)
`nurse`, `pharmacist`, `operator`, `auditor`, `admin`: one role each (the name says which). Password for all of them: `DoseRunner-2026`.
```bash
curl -s -X POST localhost:8081/auth/login -H 'Content-Type: application/json' \
     -d '{"username":"nurse","password":"DoseRunner-2026"}'     # → {"accessToken":"…","tokenType":"Bearer","expiresIn":900}
```

## REST endpoints (frozen at `v0.1-contracts`)

Roles: `NURSE`, `PHARMACIST`, `OPERATOR`, `AUDITOR`, `ADMIN`. All routes except the `public` ones need
`Authorization: Bearer <JWT>` issued by identity-service. Errors use the `ApiError` body.
Istio routes the gateway by prefix: `/auth`, `/admin` → identity · `/orders`, `/pharmacy`, `/formulary` → order ·
`/fleet` → dispatch · `/custody` → custody.

### identity-service (A) — 8081
| Method | Path | Role | Body → Response |
|---|---|---|---|
| POST | `/auth/login` | public | `{username, password}` → `{accessToken, tokenType, expiresIn}` |
| GET | `/.well-known/jwks.json` | public | → JWKS (RS256 public key) |
| GET | `/auth/me` | any | → `{id, username, roles[]}` |
| GET | `/admin/users` | ADMIN | → `[{id, username, roles[], enabled}]` |
| POST | `/admin/users` | ADMIN | `{username, password, roles[]}` → 201 user |
| PUT | `/admin/users/{id}/roles` | ADMIN | `{roles[]}` → user |
| PATCH | `/admin/users/{id}` | ADMIN | `{enabled}` → user |

### order-service (B) — 8082
| Method | Path | Role | Body → Response |
|---|---|---|---|
| POST | `/orders` | NURSE | `{patientRef, drugCode, qty, ward, room?}` → 201 order (status `PLACED`) · 409 if not enough stock |
| GET | `/orders/{id}` | NURSE, PHARMACIST, AUDITOR | → order + status + ETA |
| GET | `/orders?status=&ward=` | NURSE, PHARMACIST | → page of orders |
| POST | `/orders/{id}/delivery` | NURSE | `{robotId}` → 200, publishes `DeliveryConfirmed` |
| GET | `/pharmacy/queue` | PHARMACIST | → orders in `PLACED` |
| POST | `/pharmacy/orders/{id}/validate` | PHARMACIST | → 200 (`VALIDATED`), publishes `OrderValidated` + schedules `DispatchTimedOut` |
| POST | `/pharmacy/orders/{id}/reject` | PHARMACIST | `{reason}` → 200 (`REJECTED`), stock released |
| POST | `/pharmacy/orders/{id}/pickup` | PHARMACIST | `{robotId}` → 200, publishes `PickupConfirmed` |
| GET | `/formulary` | any | → drugs (cached) |
| GET | `/formulary/{code}` | any | → drug (cached) |
| PUT | `/formulary/{code}` | PHARMACIST | `{name, form, controlled}` → drug, evicts the cache |
| GET | `/pharmacy/stock` | PHARMACIST | → `[{drugCode, available, reserved}]` |

Order status: `PLACED → VALIDATED → ASSIGNED → PICKED_UP → COMPLETED`, plus `REJECTED`, `MANUAL_DELIVERY`.

### dispatch-service (C) — 8083 (+ gRPC 9090)
| Method | Path | Role | Body → Response |
|---|---|---|---|
| GET | `/fleet/robots` | OPERATOR, PHARMACIST | → `[{robotId, x, y, battery, status}]` |
| GET | `/fleet/robots/{id}` | OPERATOR | → robot + current mission |
| GET | `/fleet/missions?status=` | OPERATOR | → missions |
| GET | `/fleet/missions/{id}` | OPERATOR, AUDITOR | → mission + path |
| POST | `/fleet/missions/{id}/abort` | OPERATOR | `{reason}` → 200, publishes `MissionAborted` |
| GET | `/fleet/dlq` | OPERATOR | → messages currently in the DLQ |
| GET | `/fleet/strategy` | OPERATOR | → `{name, version}` of the active `DispatchStrategy` |
| gRPC | `FleetService/GetEta` | order-service SA | `EtaRequest` → `EtaReply` |
| gRPC | `FleetService/ReserveRobot` | order-service SA | `ReserveRequest` → `ReserveReply` |

### custody-service (D) — 8084
| Method | Path | Role | Body → Response |
|---|---|---|---|
| GET | `/custody/orders/{orderId}` | AUDITOR, PHARMACIST | → chain `[{seq, type, actorId, robotId, ts, payloadHash, prevHash, signature}]` |
| GET | `/custody/orders/{orderId}/verify` | AUDITOR | → `{valid, links, brokenAtSeq?, reason?}` |
| GET | `/custody/public-key` | AUDITOR | → Ed25519 public key (base64), for offline verification |

There is **no write endpoint**: ledger links are created only from Artemis events (ADR 0001 §12).

### MVC pages (Thymeleaf)
`/login`, `/ui/admin/users` (identity) · `/ui/orders/new`, `/ui/orders/{id}`, `/ui/pharmacy/queue` (order) ·
`/ui/fleet` (dispatch) · `/ui/audit/{orderId}` (custody).

## Messaging
Artemis addresses, queues and message properties are locked: see [deploy/artemis/README.md](deploy/artemis/README.md)
and [contracts/src/main/avro/README.md](contracts/src/main/avro/README.md).

## Docs
- [CONTRIBUTING.md](CONTRIBUTING.md) — branches, Conventional Commits, review rules, Definition of Done
- `docs/adr/` — architecture decision records
- `docs/journal/` — weekly journals
- `docs/NicheIntegration-*.md` — one per niche technology
