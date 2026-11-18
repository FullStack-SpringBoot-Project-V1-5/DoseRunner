# ADR 0001 — Contracts v0.1 (Day 2 session)

- **Status:** proposed. Accepted when the PR is approved by A, B, C, D and tagged `v0.1-contracts`.
- **Date:** 2026-11-18
- **Scope:** `contracts/` (Avro schemas, Artemis names) + the REST table in the README.

## Context
Blueprint §7 lists the schemas and their main fields. During the field-by-field read-through
("who produces it, who consumes it, can it ever be null?") we made the following decisions.
Each one is a deviation from or an addition to the blueprint. They are listed here so that every reviewer can
challenge them before the freeze.

## Decisions

1. **Every event carries `eventId` + `occurredAt` + `orderId`.**
   Artemis delivery is at-least-once (redelivery, failover). Consumers must be idempotent, and the
   `eventId` is the deduplication key. `occurredAt` replaces the blueprint's `ts` on Pickup/DeliveryConfirmed.

2. **`OrderValidated` carries `destination` and `controlled`.**
   The blueprint had only `(orderId, pharmacistId)`. In that case dispatch would have to call order-service back,
   or keep its own copy of `OrderPlaced`, to know where to drive. With both fields, dispatch stays stateless
   with respect to orders.

3. **`OrderPlaced.nurseId` added.** custody needs to know who ordered, and order-service checks it at delivery.

4. **`GridPoint {x, y}` split from `Location`.** `Location = ward + room? + point`. Paths and waypoints
   are `array<GridPoint>`, not `array<Location>`. A path cell has no ward or room, and repeating them on
   every cell would make the messages bigger for nothing.

5. **`MissionAborted.reason` is an enum with `default: UNKNOWN`.** Adding a reason later is then a
   backward-compatible change. `detail` is an optional string.

6. **`MissionCommand.seq` added**: a per-robot sequence number. It lets the HA demo *prove* that the
   commands arrive "in order, no duplicates".

7. **Robot addressing on the anycast `robot.commands`:** every command sets `robotId` and
   `JMSXGroupID=robotId`. Each robot consumes with the selector `robotId='<own id>'`. Message groups alone do
   not route a command to the *right* robot: a group sticks to whatever consumer received it first. The selector
   does the routing, and the group keeps the order when a robot has several consumers or reconnects.

8. **`RobotTelemetry` is frozen at v1** (`robotId, x, y, battery`). v2 (`status` enum with a default,
   `temperatureC` nullable) arrives in week 2 as the schema-evolution demo, together with the CI compatibility job.

9. **gRPC messages use `OrderRef` / `RobotRef`.** Optional values are `["null", T]` with `default: null`.
   `ReserveRequest.holdSeconds` defaults to 120. `ReserveRobot` is idempotent on `orderId`.

10. **Avro class trust.** Avro 1.12 refuses to load classes outside a trusted list.
    `AvroTrust.install()` trusts `tn.rnu.eniso.doserunner.contracts.*` only. The codec and marshaller
    (B, days 3–7) call it, and so must any code that decodes contract classes directly.

11. **Wire format on Artemis:** `BytesMessage` + Avro single-object encoding (the generated classes'
    `toByteBuffer()` / `fromByteBuffer()`). The test `ContractsRoundTripTest` checks the marker, the fingerprint
    and the round-trip of every contract.

12. **REST:** custody-service has **no write endpoint**. Ledger links are created only from events, so
    no HTTP caller can forge a custody link.

## Consequences
- The `contracts` jar is the only thing shared between services. Services never share entities.
- Any change after the tag goes through a PR approved by the 4 members. From week 2, the CI
  compatibility check also has to pass.
- Still to be written by B on days 3–7: `AvroMessageCodec`, `AvroMarshaller<T>`, and the `FleetService` gRPC descriptors.
