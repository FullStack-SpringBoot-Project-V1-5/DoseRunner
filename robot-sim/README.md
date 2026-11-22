# Robot simulator — C006

One Spring Boot process owns the synthetic R-01, R-02 and R-03 fleet. Run exactly
one instance; do not run a ROS executor for these IDs at the same time. This is
an in-memory Week 1 simulator with no datasource, JPA, Flyway or PostgreSQL.

`MissionCommandListener` (presentation) decodes through the shared
`AvroJmsMessages`, checks metadata and calls `SimulationFleet` (facade).
`SimulationCommand` and `SimulatedRobot` hold validation and execution rules.
The fleet schedules independent robot workers; `TelemetryPublisher` is the JMS
output adapter. No DAO is needed for volatile simulation state.

## Approved simulation model

- Each robot starts at the configured pharmacy, default (0,0), with 100% battery.
  Every new mission starts at the pharmacy, without simulating a return trip.
  Battery persists across missions within the process; each cell costs 1%, floor 0.
  Reaching 0 does not stop this synthetic simulation. No charging policy is implied.
- Waypoints exclude the pharmacy. They must be nonnegative, adjacent by Manhattan
  distance 1, and distinct, including no return to the starting cell. At most
  10,000 movements, matching C005's allocation bound. Empty routes complete
  immediately and emit no movement telemetry.
- First movement occurs no earlier than one second after activation. Subsequent
  cells take at least one second following successful publication. Monotonic time
  and a 50 ms scheduling poll avoid wall-clock changes and catch-up bursts.
- Per robot: one active mission and one waiting command. The waiting command
  starts after completion, with its own one-second initial delay. A full waiting
  slot raises an exception without accepting the command or changing sequence
  state; broker retries may accept it once a slot frees, or send it to DLQ.
  Operators must inspect/replay such DLQ commands after capacity returns.
- Positive increasing sequences may have gaps. Mission history uses parsed UUID
  keys, so changing UUID letter casing cannot create another mission identity.
  The original payload (including ID spelling and seq) is retained for this
  process's lifetime; changed casing is a conflicting payload, not an exact replay.
  An exact replay, including an old accepted mission after newer commands,
  acknowledges as a no-op.
  Changed content under an accepted missionId, or an unknown mission with a
  non-increasing sequence, rejects. No replay restarts motion or emits telemetry.

## JMS and failure behavior

The listener uses a single local transacted JMS session on `robot.commands`, with
selector `robotId IN ('R-01', 'R-02', 'R-03')`. `JMSXGroupID` provides grouping,
not robot targeting. Body/header robotId and eventType must agree; optional
orderId and group metadata must agree too. Messages must be Avro BytesMessages.
Unknown robot headers remain queued for their owner; a selected message with an
unknown/mismatched body target rejects. No broad catch-all consumer steals other
robots' work. Compartment codes are required synthetic identifiers, not enforcement
of medication custody or physical compartment security.

Successful in-memory acceptance returns before movement completes and commits the
input acknowledgment. Exceptions roll back; unchanged C004 configuration retries
three total deliveries then routes to DLQ. Exact known replays return successfully.
A crash after acceptance/before JMS commit can replay; the live process suppresses
it, but a restarted process has no such history.

Each movement publishes a persistent Avro BytesMessage to the multicast
`robot.telemetry` topic. The body has exactly frozen v1 fields robotId, x, y,
battery. Shared eventType, robotId, orderId and available traceId properties plus
JMSCorrelationID are propagated. Mission completion is logged with trace and
correlation; arrival does not establish medication handoff or complete dispatch's
persisted mission. There is no C008 telemetry consumer in this feature.

Publication failure leaves the current cell unadvanced and retries the same
telemetry after 1, 2, 4, then at most 8 seconds indefinitely. Retry count is not
limited; delay is bounded. Other robots keep progressing. An ambiguous send/commit
failure can duplicate telemetry. A slow broker slows movement: no catch-up burst.
Workers stop on application shutdown; shutdown does not drain accepted missions.

**Restart resets positions, battery, active/waiting commands and all replay history.**
An acknowledged mission can be lost; an old command can execute again after restart.
No durable exactly-once execution, crash-safe acceptance, automatic mission recovery,
or atomic state/JMS publication is provided. Retained replay history grows with
accepted missions; appropriate for the small Week 1 demo, not an unbounded service.

## Running

Build with the repository wrapper and Java 25:

```sh
export JAVA_HOME=/run/current-system/sw/lib/openjdk
export MAVEN_USER_HOME=/tmp/doserunner-maven
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl robot-sim -am package -DskipTests
rtk proxy java -jar robot-sim/target/robot-sim-0.1.0-SNAPSHOT.jar
```

External Artemis settings: `SPRING_ARTEMIS_BROKER_URL` (default
`tcp://localhost:61616?callTimeout=2000&reconnectAttempts=0`), `SPRING_ARTEMIS_USER`
and `SPRING_ARTEMIS_PASSWORD` (local defaults artemis/artemis). Existing broker
credentials remain authoritative. `SIM_PHARMACY_X/Y` must match dispatch pharmacy
configuration. `SERVER_PORT` defaults to 8085. Compose forwards the pharmacy values
from `DISPATCH_PHARMACY_X/Y` and depends only on Artemis, not PostgreSQL.

After reviewing local broker credentials/configuration, build/start only the
simulator with `rtk proxy docker compose -f deploy/compose/docker-compose.yml up
--build -d --no-deps robot-sim` (run as one line). Existing services/volumes remain.
This C006 implementation did not start it against development queues.

Health: `/actuator/health`, `/actuator/health/liveness`,
`/actuator/health/readiness`; readiness includes JMS broker connectivity.
For isolated HTTP smoke tests only, disable `sim.listener-enabled`,
`sim.scheduling-enabled`, and the JMS health indicator. Normal runtime requires
external Artemis. No public command/state management API was added.

## Verification

```sh
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl robot-sim -am \
  -Dtest=SimulationFleetTest,MissionCommandListenerTest,RobotSimApplicationSmokeTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=RobotSimulationIT verify
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository verify
```

Deterministic manual-clock tests cover ordering, targeting, battery, queue capacity,
replays/conflicts, validation, publication failure/backoff, independent/concurrent
progress and shutdown/restart limits. A focused real scheduler test measures three
movements with approved 0.8–2.0 s interval tolerance. This checks approximate pace
under test load, not a real-time guarantee.

`RobotSimulationIT` loads unchanged project broker XML in disposable Artemis 2.44.0
Testcontainers. It sends real Avro commands, receives and decodes persistent v1
telemetry, verifies metadata, waiting order and duplicate no-ops, tests malformed
and conflicting delivery counts 1/2/3 then DLQ, and checks HTTP health/readiness
without a datasource. An outage test stops and restarts only that disposable
broker, requiring readiness 200 -> 503 -> 200 while liveness remains 200.
A real-scheduler unit test blocks publication, closes the simulator and checks
interruption, waiting-mission cancellation and no further movement; transport is
mocked for that deterministic shutdown test.
It needs Docker access; it never uses development queues.
No hosted CI, Compose image execution or cross-service I3 demo is claimed here.
