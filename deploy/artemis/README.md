# Artemis broker config (owner: C)

## Addresses — LOCKED at `v0.1-contracts`
Java constants: `contracts/.../messaging/Addresses.java` and `MessageHeaders.java`. `broker.xml` must match them.

| Address              | Routing   | Queues                                                    | Payload                 |
|----------------------|-----------|-----------------------------------------------------------|-------------------------|
| `doserunner.events`  | multicast | `order-service`, `dispatch-service`, `custody-service`    | all `events/*.avsc`     |
| `robot.commands`     | anycast   | `robot.commands` (grouped by `JMSXGroupID=robotId`)       | `MissionCommand`        |
| `robot.telemetry`    | multicast | `dispatch-service`                                        | `RobotTelemetry`        |
| `DLQ`                | anycast   | `DLQ`                                                     | after 3 failed deliveries |
| `ExpiryQueue`        | anycast   | `ExpiryQueue`                                             | expired messages        |

Consumers subscribe to multicast queues with the FQQN `address::queue`, e.g. `doserunner.events::dispatch-service`.

## Message properties (every message)
`BytesMessage` with an Avro single-object body, plus `eventType` (record simple name), `traceId`, `orderId`;
`robotId` + `JMSXGroupID` on `robot.commands`; `_AMQ_SCHED_DELIVERY` for `DispatchTimedOut`.

## To do (days 3–7)
`broker.xml` with the table above, `address-settings` (`max-delivery-attempts=3`, redelivery delay + multiplier,
`dead-letter-address=DLQ`, `expiry-address=ExpiryQueue`), then the live/backup HA pair in week 2.
Day 1–2 compose runs the stock image with default settings.
