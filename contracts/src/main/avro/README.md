# Avro schemas — frozen at tag `v0.1-contracts`

Generated into `tn.rnu.eniso.doserunner.contracts.{common,events,robot,grpc}` by `avro-maven-plugin`
(`./mvnw -pl contracts install`). **Any change = PR approved by all 4.** Decisions: `docs/adr/0001-contracts-v0.1.md`.

| Schema              | Producer                | Consumers                           | Where                          |
|---------------------|-------------------------|-------------------------------------|--------------------------------|
| `common/GridPoint`  | —                       | —                                   | grid cell (x, y)               |
| `common/Location`   | —                       | —                                   | ward, room?, point             |
| `common/OrderRef`, `common/RobotRef` | —      | —                                   | gRPC messages                  |
| `OrderPlaced`       | order                   | custody                             | `doserunner.events`            |
| `OrderValidated`    | order                   | dispatch                            | `doserunner.events`            |
| `RobotAssigned`     | dispatch                | order, custody                      | `doserunner.events`            |
| `PickupConfirmed`   | order (pharmacist scan) | custody (link #1), dispatch         | `doserunner.events`            |
| `DeliveryConfirmed` | order (nurse scan)      | custody (link #2), dispatch         | `doserunner.events`            |
| `DispatchTimedOut`  | order (scheduled T+120s)| order                               | `doserunner.events`            |
| `MissionAborted`    | dispatch                | order, custody (ABORT), dispatch    | `doserunner.events`            |
| `MissionCommand`    | dispatch                | robot-sim, ros-bridge               | `robot.commands` (anycast)     |
| `RobotTelemetry` v1 | robot-sim, ros-bridge   | dispatch                            | `robot.telemetry` (multicast)  |
| `EtaRequest/Reply`  | order → dispatch        |                                     | gRPC `FleetService.GetEta`     |
| `ReserveRequest/Reply` | order → dispatch     |                                     | gRPC `FleetService.ReserveRobot` |

Every event starts with `eventId`, `occurredAt`, `orderId`.
