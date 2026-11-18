# Avro schemas — written together on Day 2 (B drives), frozen at tag `v0.1-contracts`

Namespace: `tn.rnu.eniso.doserunner.contracts.*` · any change = PR approved by all 4.

| Folder    | Files                                                                                                  | Artemis address                         |
|-----------|--------------------------------------------------------------------------------------------------------|-----------------------------------------|
| `common/` | `OrderRef`, `RobotRef`, `Location`                                                                     | —                                       |
| `events/` | `OrderPlaced`, `OrderValidated`, `RobotAssigned`, `PickupConfirmed`, `DeliveryConfirmed`, `DispatchTimedOut`, `MissionAborted` | `doserunner.events` (multicast)         |
| `robot/`  | `MissionCommand`, `RobotTelemetry` (v1 → v2)                                                            | `robot.commands` (anycast, group=robotId) / `robot.telemetry` (multicast) |
| `grpc/`   | `EtaRequest`, `EtaReply`, `ReserveRequest`, `ReserveReply`                                              | gRPC `FleetService` (order → dispatch)  |
