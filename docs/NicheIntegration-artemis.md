# Apache ActiveMQ Artemis integration — Gueddes

This describes the implemented Week 1 dispatch/robot slice at `4aa92f6`, checked
on 2026-11-24. `feature_list.json` tracks acceptance. Artemis supplies durable
queues, asynchronous delivery, robot grouping, retries and dead letters; Avro
supplies the message encoding. Neither a healthy broker nor persistent messages
alone establish successful mission execution.

## Application flow and ownership

```text
OrderValidated -> doserunner.events::dispatch-service
  -> OrderValidatedListener -> OrderValidatedFacade -> DispatchFacade
  -> fleet PostgreSQL: mission + BUSY robot + inbox + sequence + outbox
  -> OutboxRelay -> DispatchEventPublisher
       RobotAssigned -> doserunner.events
       MissionCommand -> robot.commands -> robot-sim
  -> RobotTelemetry v1 -> robot.telemetry::dispatch-service.telemetry
  -> RobotTelemetryListener -> RobotTelemetryFacade -> robot measurements
  -> /ui/fleet (two-second HTML refresh)
```

Presentation adapters call facades, which own database transaction boundaries;
business objects perform selection/routing and DAOs persist data. The strategy
is discovered through Java `ServiceLoader`. It selects an IDLE robot with battery
at least 20%, using Manhattan distance to the configured pharmacy and then
lexicographic robot ID. Routes walk X then Y from the pharmacy, omit the origin
from command waypoints, and have a 10,000-cell movement bound. ETA is one second
per movement; robot-to-pharmacy travel is excluded. Compartment C1 mappings are
synthetic demo data, not controlled-medication enforcement.

Gueddes's slice owns dispatch, broker configuration, simulator and RobotLink;
order validation, shared security and custody integration retain their respective
team owners. The tests below supply synthetic inputs, not real patient records.

## Addresses, queues and wire contract

Names come from [Addresses.java](../contracts/src/main/java/tn/rnu/eniso/doserunner/contracts/messaging/Addresses.java)
and [broker.xml](../deploy/artemis/broker.xml).

| Address | Routing | Durable queues / consumers |
| --- | --- | --- |
| `doserunner.events` | MULTICAST | `order-service`, `dispatch-service`, `custody-service` |
| `robot.commands` | ANYCAST | `robot.commands`, robot executor |
| `robot.telemetry` | MULTICAST | `dispatch-service.telemetry`, dispatch |
| `DLQ` | ANYCAST | `DLQ` |
| `ExpiryQueue` | ANYCAST | `ExpiryQueue` |

Multicast consumers use fully qualified queue names (`address::queue`). The
dispatch event queue and listener select `eventType = 'OrderValidated'`; the
other event queues retain their own copies. The distinct telemetry queue name
is the approved C004 correction because broker queue names are globally unique.

Bodies are JMS `BytesMessage` containing Avro single-object encoding, using the
shared [AvroJmsMessages](../contracts/src/main/java/tn/rnu/eniso/doserunner/contracts/messaging/AvroJmsMessages.java)
and `AvroMessageCodec`. Plain text or JSON is not a valid mission/event body.
`eventType` is the Avro record name; `orderId` and `traceId` correlate dispatch
outputs. Missing dispatch input trace IDs get a logged replacement, so send the
original trace ID when cross-service correlation matters.

Commands also set `robotId` and `JMSXGroupID=robotId`. **Groups preserve ordering;
selectors provide targeting.** The simulator has one transacted consumer with
`robotId IN ('R-01', 'R-02', 'R-03')`; a RobotLink executor selects its exact ID.
Run only one executor owner for each robot. The simulator validates body/header
identity and serializes each robot's accepted work. Unknown header targets stay
queued for their owner. Dispatch's publisher does not set `JMSCorrelationID`;
the simulator propagates it when supplied, alongside trace/order metadata.

`RobotTelemetry` v1 contains only robotId, x, y and battery. Dispatch updates
only x/y/battery on an existing robot, preserving BUSY/ASSIGNED status. Latest
arrival wins: delayed readings can regress the display. No v1 completion,
timestamp, sequence or custody confirmation is inferred. `/ui/fleet` shows
robots and active missions; it does not currently provide a DLQ browser.

## Broker configuration and retry behavior

Tests use `apache/activemq-artemis:2.44.0`, the same pinned broker image as Compose.
The project XML declares five addresses/seven durable queues and retains the
persistent ASYNCIO journal, protocol acceptors and `amq` security role. Compose
exposes messaging on 61616 and the management console on 8161. Credentials come
from the existing instance; the application's artemis/artemis defaults are not
proof that an existing broker accepts them.

The application catch-all address setting has:

| Setting | Value |
| --- | --- |
| `max-delivery-attempts` | 3 total deliveries, including the first |
| `redelivery-delay` | 1000 ms |
| `redelivery-delay-multiplier` | 2.0 |
| `max-redelivery-delay` | 10000 ms |
| `dead-letter-address` | `DLQ` |
| `expiry-address` | `ExpiryQueue` |

Thus failure 1 retries after about one second, failure 2 after about two seconds,
and failure 3 dead-letters. The 10-second cap is not reached by those two retries.
DLQ/ExpiryQueue override the attempt limit to -1 (unlimited). The stock management
address has separate settings. Configuring an expiry destination does not assign
a TTL: this slice does not add business-message expiration.

Listener factories use local transacted JMS sessions. Successful processing
returns for acknowledgment; an exception rolls delivery back. The simulator
acknowledges after in-memory acceptance, before motion completes. Broker retry
delays are separate from telemetry publication backoff (1/2/4/8 seconds, delay
capped at 8 seconds with unlimited retry attempts).

[start-broker.sh](../deploy/artemis/start-broker.sh) copies the XML override for an
existing instance before delegating to the image entrypoint; fresh instances use
the image's initialization. A restart is needed for XML changes. Existing queue
filters require a coordinated in-place update; restarting is not evidence of a
filter update. The demos below use disposable brokers, so no live rollout is
needed. See the [broker rollout notes](../deploy/artemis/README.md) before any
separately authorized change. Never purge/recreate development queues, reset
databases or remove volumes to reproduce a test.

## Reproduce consumption, movement and DLQ behavior

Run from the repository root with Docker available and the project's NixOS JDK
25 path below (use your installed JDK 25 path elsewhere). Maven comes from the
wrapper. A first run needs network access to resolve dependencies/pull images.
These tests allocate disposable PostgreSQL 16.16 and Artemis 2.44.0 containers
with mapped ports, load the actual broker XML/startup wrapper, and clean up
their containers. They do not use development service credentials or queues.

```sh
export JAVA_HOME=/run/current-system/sw/lib/openjdk
export MAVEN_USER_HOME=/tmp/doserunner-maven
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl dispatch-service,robot-sim -am \
  -Dtest=DispatchArchitectureTest,DispatchArchitectureRulesTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=ArtemisBrokerIT,DispatchMessagingIT,RobotSimulationIT verify \
  > /tmp/doserunner-artemis-demo.log 2>&1
rtk proxy rg 'Received OrderValidated|MissionCommand accepted|C009 poison|C004 DLQ|Tests run:|BUILD SUCCESS' /tmp/doserunner-artemis-demo.log
```

The command must exit successfully; expected error logs from poison inputs are
not test failures. Check the final Maven result and XML reports, not merely a
matching log line. The observed focused total is **38 tests: 17 Surefire and 21
Failsafe**, zero failures/errors/skips. Evidence locations are
`dispatch-service/target/{surefire-reports,failsafe-reports}/` and
`robot-sim/target/failsafe-reports/`.

| Demonstration | Observable assertion / output |
| --- | --- |
| `DispatchMessagingIT.consumesPublishesAndDeduplicatesRealAvroMessages` | Real OrderValidated received with `integration-trace`; one persisted mission/BUSY robot; decoded RobotAssigned and MissionCommand, matching mission/order IDs, persistent delivery, R-01 targeting/grouping, sequence 1, ETA 5, path to (2,3); duplicate input has no second assignment. |
| `DispatchMessagingIT.rejectsInvalidPayloadsAndUnavailableFleetThroughRollbackAndDlq` | Bad Avro, text, invalid UUID, conflicting metadata and no eligible robot each produce `C009 poison kind=0` through `kind=4`: attempts 1/2/3, delayed retries, one preserved DLQ copy, no fourth delivery, no partial database effects. |
| `ArtemisBrokerIT` | Loaded XML byte equality, live bindings/filter/durability and management-reported settings; exact numeric values reject prefixes such as 100000 for 10000. Direct rollback probe logs `C004 DLQ received after exactly 3 failures`. |
| `RobotSimulationIT.brokerBackedMovementTargetingWaitingOrderAndReplay` | Real commands and decoded v1 readings: R-01 (1,0,99), (2,0,98), then queued mission (0,1,97); R-02 independently (0,1,99). Exact replay produces no additional movement telemetry. |
| Remaining `RobotSimulationIT` cases | Four invalid command cases reach DLQ, no database is required, and a disposable broker outage changes readiness 200 -> 503 -> 200 while liveness stays 200. |
| Architecture suites | Nine production-layer rules plus eight direct/composed Spring/Jakarta transaction regression cases. |

These are separate dispatch and simulator test fixtures, not a single connected
order-service-to-simulator deployment. The simulator integration uses its real
scheduler; the root unit suite also measures approximate one-second movement
intervals. Full UI validation with Chbel and shared trace propagation is
the separately tracked C011 integration checkpoint.

Return to full core verification:

```sh
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository verify
```

The full reactor total is **193 tests: 166 Surefire and 27 Failsafe**, zero
failures/errors/skips. The separate [Java 11 RobotLink build](../robot-sim/robot-link/README.md)
is outside that reactor; its README defines its own build/runtime checks. Core
Java 25 classes are not the Java 11 handoff. ROSJava execution remains Knani's
integration responsibility.

## Transactions, recovery and limits

Dispatch atomically saves mission/BUSY state, input event ID/hash, per-robot
sequence and immutable outbox payloads in PostgreSQL. Reusing an event ID with
different content rejects. If the DB commits before input JMS acknowledgment and
the input is redelivered, the stored receipt prevents repeated assignment.
`OutboxRelay` serializes publication with a database
coordination lock; both outgoing messages commit in one local JMS transaction,
then the outbox row is marked sent in PostgreSQL. A pending row is retried after
errors/restart. The default scheduling delay is 1000 ms and the global lock is
held during broker I/O, limiting throughput and causing head-of-line blocking.

**PostgreSQL and JMS do not share an atomic transaction.** A crash after JMS
commit and before marking the outbox sent resends the same pair. Stable
`_AMQ_DUPL_ID` values reduce duplicates only while their IDs remain in the finite
Artemis cache. Cache eviction/clearing can permit duplicates; do not reset IDs,
inbox/outbox rows or command sequences during recovery. No exactly-once execution
or broker HA claim follows from this design.

The simulator retains accepted mission UUIDs/sequences only in memory, with one
active and one waiting command per robot. Restart loses acknowledged work,
positions, battery and replay history; an old command can execute again. An
ambiguous telemetry send can duplicate a reading. Durable acceptance/progress and
restart reconciliation were discussed as possible future work, not implemented
or agreed recovery semantics. See [simulator details](../robot-sim/README.md).

A valid order with no eligible robot, or a simulator command with a full waiting
slot, can exhaust retries and reach DLQ. Returning capacity does not replay it
automatically. Inspect its event/mission ID, trace, original queue and current
database/outbox state first. Let staged outbox work recover normally. For a
confirmed dispatch input requiring replay, use the broker's single-message
`retryMessage` operation, preserving original IDs/body/properties; never bulk
retry blindly. The [dispatch recovery instructions](../dispatch-service/README.md#safe-manual-replay-after-no-eligible-robot)
describe the checks. This document does not perform live replay or authorize
changes to another service's messages.

For a separately arranged application demo, both Spring apps use
`SPRING_ARTEMIS_BROKER_URL`, `SPRING_ARTEMIS_USER` and `SPRING_ARTEMIS_PASSWORD`.
Dispatch additionally needs `SPRING_DATASOURCE_URL`,
`SPRING_DATASOURCE_USERNAME` and `SPRING_DATASOURCE_PASSWORD` for the fleet DB.
`DISPATCH_PHARMACY_X/Y` must match `SIM_PHARMACY_X/Y` when launching jars;
Compose forwards the same dispatch pharmacy inputs to both apps. Do not assume
fresh seed state in a persistent fleet database. See the
[dispatch run/configuration notes](../dispatch-service/README.md) and
[simulator running instructions](../robot-sim/README.md#running). The disposable
tests above configure their own endpoints and do not require those overrides.

The fleet UI has the standalone authentication baseline; shared JWT integration
is C012. Advanced sagas/timeouts, durable simulator recovery, automatic
waiting-for-capacity dispatch, ROS execution and HA remain outside this feature.

## Evidence boundary

C010 changes documentation only. Commands above are validated locally against
the checked-out implementation; source references and queue/settings names are
reviewed against code/XML. Detailed dated commands, environments and counts are
recorded in local `feature_list.json`, `progress.md` and `session-handoff.md`.
Test logs are temporary conveniences, not required build inputs. No hosted CI,
new live Compose rollout, full shared-service I3 or Week 1 gate completion is
claimed. Health endpoints alone do not establish message consumption.
