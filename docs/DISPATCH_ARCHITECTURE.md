# DoseRunner — Gueddes architecture slice

**Status:** Week 1 implementation guide; reconcile with the actual Day 2 code before coding.
**Scope:** dispatch-service, Artemis, robot-sim, ROSJava-facing RobotLink.
**Primary requirements:** `docs/PRODUCT.md` if present, frozen contracts, and `docs/NOTION_WEEK1_GUEDDES.md`.

## Runtime relationships

```mermaid
flowchart TD
    ORD["order-service: pharmacist validates"] -->|"OrderValidated Avro BytesMessage"| EVT["Artemis: doserunner.events"]
    EVT --> LST["dispatch-service: JMS adapter"]
    LST --> FCD["Dispatch facade"]
    FCD --> STR["DispatchStrategy via ServiceLoader"]
    FCD <--> DB[("PostgreSQL fleet DB")]
    FCD -->|"RobotAssigned"| EVT
    FCD -->|"MissionCommand"| CMD["Artemis: robot.commands"]
    CMD --> SIM["robot-sim"]
    SIM -->|"RobotTelemetry v1"| TEL["Artemis: robot.telemetry"]
    TEL --> LST
```

`RobotLink` is a separate pure-JMS client interface for the ROSJava teammate, **not** a dependency of Spring controllers or the simulator. It must use the agreed messaging contracts and support the Java 11 runtime used by Knani.

## Course pyramid in dispatch-service

```mermaid
flowchart TD
    PRES["Presentation: REST/Thymeleaf/JMS listener"] --> FAC["Facade: mission transaction + orchestration"]
    FAC --> BO["Business objects: eligibility and strategy"]
    BO --> DAO["DAO: Robot/Mission repositories"]
    DAO --> DB[("fleet PostgreSQL")]
    FAC --> MSG["Messaging adapter: Artemis publisher"]
```

Exact package names are **not specified here**; follow the existing project's `pres`, `facade`, `bo`, `dao` conventions and ArchUnit rules.

## Responsibilities and expected behavior

| Piece | Week 1 responsibility | Evidence |
|---|---|---|
| `Robot`, `Mission` | Persist robots, missions, and three demo robots via Flyway | PostgreSQL Testcontainers migration/query test |
| `DispatchStrategy` | Select nearest *eligible idle* robot through Java `ServiceLoader` | Unit tests, including tie/no-eligible cases where behavior is specified |
| Dispatch facade | Coordinate selection and mission persistence in a local transaction | Deterministic facade tests; duplicate-delivery handling defined |
| `@JmsListener` | Decode `OrderValidated`, preserve IDs/trace headers, delegate to facade | Artemis Testcontainers integration test |
| Publisher | Emit `RobotAssigned` and `MissionCommand` using frozen Avro schemas | Decode produced messages with shared codec |
| Artemis broker config | Existing addresses/queues, 3 failed deliveries, delayed retry multiplier, DLQ | Broker configuration + poison-message DLQ test |
| `robot-sim` | Consume commands, simulate 1 cell/s, publish `RobotTelemetry` v1, no DB | Observable sequence of telemetry positions |
| `RobotLink` | Spring-free JMS access for Knani, Java 11-compatible artifacts | Java 11 compilation/runtime compatibility proof |
| Fleet UI | Thymeleaf robot/mission view, 2-second page refresh | Manual or UI test with changing positions |

## Message flow and semantics

1. B publishes a **frozen-contract** `OrderValidated` event on `doserunner.events` (multicast).
2. C's listener validates/decodes it using the shared Day 2 Avro codec; avoid incompatible alternate encodings.
3. C003 serializes assignment through stable full-fleet robot-row locks inside the facade transaction, refreshes robot state and checks order history after locking. Existing ASSIGNED missions are returned; terminal-only history rejects implicit reassignment. No eligible robot returns an empty result without writes and permits a later retry. These rules were approved on 2026-11-21. Full-fleet locking limits throughput; see `dispatch-service/README.md`. Durable eventId deduplication and DB/message publication consistency remain for C005.
4. The strategy selects an eligible idle robot; the facade persists the mission and robot changes within its transaction.
5. C emits `RobotAssigned` to the event address and `MissionCommand` to `robot.commands` (anycast), setting `JMSXGroupID=robotId`.
6. The simulator receives commands intended for it, moves over waypoints, and publishes telemetry on `robot.telemetry` (multicast).
7. C can consume telemetry and update fleet state for the 2-second-refresh dashboard.

Important: `JMSXGroupID` groups messages on a consumer for **ordering**; it does not independently route a message to the intended robot when multiple robot consumers share a queue. Agree on robot-specific targeting with Knani before choosing queues/selectors/filters. Do not claim exactly-once processing from Artemis defaults.

## Explicit unknowns (do not silently decide)

- Coordinates and distance metric; whether obstacles/waypoints are defined in Day 2 contracts.
- Battery eligibility threshold, robot/mission statuses and transition constraints.
- Mission command targeting per robot beyond message grouping.
- Durable event/message decoding (writer fingerprints), event ID deduplication, and DB/message consistency after a crash.
- Error path when no robot is eligible and the event's correct acknowledgment/redelivery policy.
- Exact contracts for the RobotLink Java 11-generated class artifact.

Week 1 goal is a demonstrable, tested vertical slice. Timeout sagas, native route A*, gRPC, full custody handoffs, Istio, and broker high availability are not authorized additions to this Week 1 implementation slice.
