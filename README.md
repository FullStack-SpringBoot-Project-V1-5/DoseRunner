# 💊 DoseRunner

Hospital medication logistics: a nurse orders a medication, a pharmacist validates it, an autonomous robot
carries it to the ward, and every handoff is signed in a tamper-evident custody ledger.

Programming Frameworks project (IA3) · Spring Boot 4.1 · Java 25 · Istio · Avro · ActiveMQ Artemis · FFM · ROSJava

## Modules

| Module                 | Owner | What                                                                 | Port |
|------------------------|-------|----------------------------------------------------------------------|------|
| `contracts`            | B     | Avro schemas + gRPC descriptors (frozen, change = PR approved by 4)  | —    |
| `commons`              | joint | Pyramid transversal layer: dto, sec, trace, util, `logback-spring.xml` | —    |
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
docker compose -f deploy/compose/docker-compose.yml up --build -d
curl http://localhost:8082/actuator/health      # {"status":"UP",...}
docker compose -f deploy/compose/docker-compose.yml logs -f order-service   # JSON logs with traceId
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

## REST endpoints
_To be filled during the Day 2 contracts session (method · path · role · body)._

| Service | Method | Path | Role | Body |
|---------|--------|------|------|------|

## Docs
- [CONTRIBUTING.md](CONTRIBUTING.md) — branches, Conventional Commits, review rules, Definition of Done
- `docs/adr/` — architecture decision records
- `docs/journal/` — weekly journals
- `docs/NicheIntegration-*.md` — one per niche technology
