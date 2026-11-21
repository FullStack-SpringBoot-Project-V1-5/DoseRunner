# Dispatch service — C001/C002/C003

Flyway creates `robots` and `missions` in the fleet PostgreSQL database; Hibernate
validates the mappings (`ddl-auto: validate`). Local defaults are
`jdbc:postgresql://localhost:5432/fleet`, user/password `fleet`; override with
`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
(as the existing Compose service already does). Startup now requires PostgreSQL.
No persistent development database was migrated during C001 verification.

The migration seeds exactly three synthetic robots: R-01 at (0,0), battery 100;
R-02 at (5,0), battery 80; R-03 at (0,5), battery 60. All start IDLE.
These are demo positions, not an agreed hospital map or eligibility policy.
Flyway runs the seed once; restarting does not reset robot state.

The local model was explicitly approved by Wassim during C001 on 2026-11-21:

- Robot: fleet string ID (up to 64 characters), nonnegative integer x/y,
  battery percentage 0–100, status IDLE/BUSY/OFFLINE.
- Mission: caller-supplied UUID id and orderId, required robot reference,
  status ASSIGNED/COMPLETED/ABORTED. No cascade removal of robots or missions.
- These statuses are persistence vocabulary, not additions to the frozen Avro
  schemas. General transition rules, route/command fields and event
  deduplication remain for their owning features. There is no unique
  order constraint: future reassignment may create another mission for an order.

Entities and Spring Data repositories live in `dao`, per its existing package
contract. There are no HTTP/JMS callers yet. Frozen Day 2 contracts remain intact:
RobotRef uses fleet strings, RobotTelemetry v1 uses integer x/y/battery,
RobotAssigned identifies missions as UUID strings. Convert UUIDs to strings at
future messaging adapters; no JPA classes are shared with other services.

From the repository root, with Java 25 and Docker available:

```sh
export JAVA_HOME=/run/current-system/sw/lib/openjdk
export MAVEN_USER_HOME=/tmp/doserunner-maven
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl dispatch-service -am -Dtest=RobotTest,FleetPersistenceTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository verify
```

Tests require Docker and use disposable `postgres:16.16` Testcontainers databases,
never the Compose fleet database. The persistence tests verify the migration and
seeds, repeat migration without reseeding, flushed/cleared JPA round trips and
updates, and database constraints including robot foreign-key protection.
The existing HTTP smoke tests also run against isolated PostgreSQL.


## Dispatch selection — C002

Wassim approved these v1 rules on 2026-11-21:

- Eligible robots are IDLE with battery **at least 20%** (inclusive).
- Rank by Manhattan distance `abs(robot.x - pickupX) + abs(robot.y - pickupY)`
  to a **caller-supplied pickup/pharmacy point**, not the delivery destination.
- Break equal-distance ties by the lexicographically smallest robot ID using
  Java string ordering, independently of input order or battery above threshold.
- This is grid distance without obstacle routing; it is not a route or ETA.

`bo.DispatchStrategy` accepts immutable `RobotCandidate` snapshots and pickup
coordinates, returning `Optional<RobotCandidate>`; empty means no eligible robot.
Candidates validate fleet IDs, nonnegative coordinates, battery 0–100 and status.
The snapshot reuses the plain C001 `RobotStatus` enum, but carries no JPA entity.
The provider is stateless, does not mutate candidates or reserve a robot, and
uses `long` distance arithmetic to avoid integer overflow. Selection is O(n).
Null fleets/entries/statuses are rejected; invalid IDs/coordinates/battery throw
`IllegalArgumentException`. A fleet should contain one snapshot per robot ID.

`NearestIdleRobotStrategy` is a public no-argument provider, registered in
`src/main/resources/META-INF/services/tn.rnu.eniso.doserunner.dispatch.bo.DispatchStrategy`.
Discover it through the Java SPI, with no Spring context:

```java
DispatchStrategy strategy = ServiceLoader.load(DispatchStrategy.class)
        .findFirst().orElseThrow();
Optional<RobotCandidate> selected = strategy.select(candidates, pickupX, pickupY);
```

The discovery test instantiates and executes the registered provider. The unit
suite covers Manhattan vs Euclidean ranking, supplied pickup location, zero and
large distances, battery thresholds, BUSY/OFFLINE exclusions, lexicographic ties
in both input orders, empty/all-ineligible fleets, immutability and invalid input.
Run it with the Java/Maven environment above:

```sh
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl dispatch-service -am -Dtest=NearestIdleRobotStrategyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

`PackagedDispatchStrategyIT` runs under Maven Failsafe during `verify`, after the
Boot JAR is packaged. It loads the SPI, descriptor and provider from that JAR's
`BOOT-INF/classes` using an isolated class loader with only the JDK as parent,
so application classes on the test classpath cannot mask packaging regressions.
It discovers, instantiates and invokes v1 without starting Spring or PostgreSQL.
To run both discovery checks with the Java/Maven environment above:

```sh
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl dispatch-service -am -Dtest=NearestIdleRobotStrategyTest \
  -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=PackagedDispatchStrategyIT verify
```

C002 itself only selects robots. The C003 facade below supplies current snapshots
and transactional assignment; its caller supplies the actual pharmacy point. The
`OrderValidated.controlled` field remains unchanged; additional compartment
eligibility rules have not been specified or implemented by this v1 selector.


## Transactional mission assignment — C003

Wassim approved these rules on 2026-11-21. The Spring-managed
`facade.DispatchFacade.assignMission(UUID orderId, int pickupX, int pickupY)`
returns `Optional<Mission>`:

- If an ASSIGNED mission already exists for the order, return it without another
  assignment, even if the supplied pickup differs or no robot is now eligible.
- If history contains only COMPLETED/ABORTED missions, throw `IllegalStateException`;
  implicit reassignment is forbidden. Future explicit reassignment is not implemented.
- With no history, select using the C002 rules, create a random UUID mission with
  status ASSIGNED and set the selected robot BUSY in the same local transaction.
- No eligible robot returns `Optional.empty()` without writes; a later request can retry.
- Null order IDs and negative pickup coordinates fail before repository access.

The public facade method owns the READ_COMMITTED transaction. It locks **all robot
rows**, including BUSY/OFFLINE robots, using PESSIMISTIC_WRITE in database ID order.
After acquiring locks it refreshes managed robot state, then checks mission history
and invokes the strategy. Refresh prevents cached entities in an existing calling
transaction from supplying stale candidates; callers must not pass pending robot
edits into assignment, since refresh discards unflushed entity changes. Locks remain
held until transaction completion (the caller's completion when joining a transaction).
Call the Spring proxy, not a manually constructed instance, for transaction guarantees.
An enclosing transaction must use READ_COMMITTED as well.

This serializes assignments across dispatch instances sharing PostgreSQL. A waiting
request sees the committed BUSY robot and the newly committed mission before making
its decision. Consistent lock order reduces deadlocks between assignment calls.
**Trade-off:** unrelated orders wait for each other; throughput is bounded by one
full-fleet assignment transaction at a time. This is approved for the small Week 1
fleet; revisit reservation/locking design before scaling. Robot provisioning/deletion
must not race assignment; row locks do not lock absent rows or prevent new fleet rows.
There is no schema migration, global UNIQUE(order_id), or database constraint enforcing
this protocol: all assignment writers must go through this facade. Existing historical
missions remain permitted. General mission transitions are outside C003.

The facade orchestrates BO selection and DAO persistence; no presentation adapter
exists yet. `config.DispatchConfiguration` discovers the approved v1 provider through
ServiceLoader and exposes it as the strategy bean. No dependency was added. Frozen
Avro schemas, REST contracts, and other services are unchanged.

Order-level repeat handling here is **not durable eventId deduplication**. C005 must
implement the frozen delivery-deduplication semantics and resolve database/message
publication consistency; C003 has no JMS listener, publisher, outbox, route or ETA.
The returned entity is a local model, not a wire DTO; lazy robot fields require an
active persistence context (its ID is available for identifying the assignment).

Focused verification, with the Java/Maven environment above and Docker access:

```sh
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -pl dispatch-service -am -Dtest=DispatchFacadeTest,DispatchAssignmentTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Unit tests cover orchestration, validation, repeat/terminal history and non-assignment.
PostgreSQL tests cover persisted selection, retry after unavailability, duplicate and
terminal requests, cached-state refresh, and a deferred trigger that verifies BUSY
was flushed before raising a commit failure: neither write survives rollback.
Concurrency tests hold a first transaction open and observe a second transaction's
actual PostgreSQL lock wait before allowing commit. They verify different orders
choose different robots, a single eligible robot cannot serve both orders, and same-order
requests return the same mission. Tests use only disposable Testcontainers databases.

## C005 — OrderValidated and durable publication

The listener consumes `doserunner.events::dispatch-service` with selector
`eventType = 'OrderValidated'`. It uses the imported Avro single-object
`BytesMessage` adapter, validates canonical UUID event/order identifiers and
required fields, rejects conflicting orderId properties, preserves traceId or
logs a generated replacement, and restores the previous MDC on success/failure.
A local transacted JMS session acknowledges only after processing returns;
exceptions roll back for the broker's configured three deliveries then DLQ.

### Approved Week 1 behavior

- Pharmacy is configurable, default `(0,0)`. `RoutePlanner` is replaceable;
  `ManhattanRoutePlanner` walks X first, then Y without obstacle assumptions.
- RobotAssigned.path includes both endpoints. MissionCommand.waypoints omits
  the pharmacy; identical endpoints mean singleton path, empty waypoints, ETA 0.
  ETA counts grid movements at one cell/second. Robot-to-pharmacy travel is excluded.
- Coordinates must be nonnegative. A materialized route is limited to 10,000
  movements to bound allocations from untrusted coordinates; this is not a map.
- Each robot has a durable integer command sequence starting at 1. Allocation
  rolls back with assignment; retries reuse the stored sequence. Exhaustion
  fails explicitly rather than wrapping.
- Synthetic compartment mappings R-01/R-02/R-03 -> C1 are demo identifiers only.
  There is no controlled-medication compartment enforcement. Missing mappings fail.
- No eligible robot is a processing failure: a temporarily busy fleet can send a
  valid order to DLQ. Capacity returning does **not** automatically replay it.

### Transactions and recovery

V2 adds `dispatch_inbox`, `dispatch_command_sequence`, `dispatch_outbox` and a
single coordination row. `OrderValidatedFacade` locks that row, invokes the
unchanged C003 Spring facade inside READ_COMMITTED, and atomically commits:
mission/BUSY state, event UUID and canonical payload hash, allocated sequence,
and one immutable outgoing Avro pair per mission. Existing active C003 missions
without an outbox pair receive their first pair; an existing pair is never
replaced for another event ID. C003 terminal-history rules remain unchanged.
Reusing an eventId with different payload is rejected. Receipt records are kept;
no automatic retention/deletion policy is introduced.

A database-serialized relay selects the oldest pending pair and publishes a
persistent RobotAssigned topic message and MissionCommand queue message in one
**local JMS transaction**, then marks the database row sent. Topic/queue routing
is explicit. Both carry eventType/orderId/traceId; commands also carry robotId
and JMSXGroupID=robotId. Grouping does not replace the robot consumer selector.

Publication IDs, timestamps, bytes, and sequences are stored once. Stable
`_AMQ_DUPL_ID` values are `dispatch:<mission UUID>:assigned` and `:command`.
An Artemis duplicate-transaction rejection confirms the previously sent pair
and allows the relay to complete its database record. Other failures propagate
out of the relay transaction; the scheduler logs them and retries pending work.
A new application context resumes pending rows without an in-memory queue.

This is **not an atomic PostgreSQL/JMS transaction or exactly-once delivery**.
A crash after broker commit but before database commit resends the same pair.
Artemis duplicate suppression works only while those IDs remain in its finite,
per-address cache; eviction/administrative clearing can permit duplicate delivery.
C006 must deduplicate commands by missionId/seq. Broker redelivery is also possible.
Do not clear the cache or generate replacement publication IDs during recovery.
The cache must remain enabled and persisted for restart suppression; C004's stock
cache settings are preserved. The C005 tests exercise duplicate suppression on
2.44.0, not unlimited cache retention or broker HA.

The global database lock deliberately serializes receipt and relay across
instances, holds through broker I/O, and preserves committed sequence order.
Default relay throughput is one pair per second, with head-of-line blocking on
failure. Revisit this Week 1 design before scaling. Inbox/outbox/sequence rows
must not be manually reset to replay work. The upstream order-service's existing
after-commit event-loss window remains outside C005.

### Runtime configuration

External Artemis is required; native mode uses `SPRING_ARTEMIS_BROKER_URL`,
`SPRING_ARTEMIS_USER`, `SPRING_ARTEMIS_PASSWORD` (local defaults tcp://localhost:61616,
artemis/artemis). Existing broker auth files remain authoritative.
`DISPATCH_PHARMACY_X` and `DISPATCH_PHARMACY_Y` override `(0,0)`.
`dispatch.messaging.compartments` is a map of robot IDs to compartment codes,
replaceable using Spring configuration. `dispatch.messaging.relay-delay-ms`
defaults to 1000. Listener/relay can be disabled separately with
`dispatch.messaging.listener-enabled=false` / `relay-enabled=false` for maintenance
or isolated database tests. Disabling the relay leaves pending rows durable.

### Safe manual replay after no eligible robot

1. Confirm capacity is now eligible and fix the original processing cause. Inspect
   the one DLQ message, its order/event UUIDs, trace, Avro body and original queue.
   It must be an OrderValidated from `doserunner.events::dispatch-service`.
2. Inspect the corresponding mission/inbox/outbox state read-only. If work is
   already staged, let the outbox relay recover it; do not create another command.
3. In the broker management console invoke DLQ QueueControl **retryMessage** with
   that one broker message ID. It retries to its original queue. Preserve the
   original eventId/body/properties. Do not use bulk retry, purge, or edit DB state.
4. Observe the same trace/order in dispatch, one mission and one publication pair.
   A still-unavailable fleet can exhaust three attempts and return to DLQ again.

Malformed/conflicting events require investigation, not blind replay. This task
has not operated on live workflow queues. Management retry API reference:
https://artemis.apache.org/components/artemis/documentation/javadocs/javadoc-latest/org/apache/activemq/artemis/api/core/management/QueueControl.html#retryMessage(long)

### Broker filter rollout

The repository XML now filters only the dispatch event queue; all other C004
bindings, telemetry correction, persistence, security and retry settings remain.
Disposable-broker tests verify the filter and that unrelated events still reach
other services. This does not retroactively remove previously queued events.

The existing persistent broker was **not modified or restarted**. Before a later
operator-authorized rollout, back up the instance/configuration, record queue
identity/count/filter and stop dispatch consumers. Apply the dispatch queue
filter in place using the running version's supported queue-update management
operation and install the matching XML for subsequent starts. Verify the filter
and retained queue/message counts. Never delete/recreate/purge the queue; if that
broker version cannot update the filter in place, stop and plan a preserving
migration separately. Previously accumulated unrelated messages need a separate
reviewed disposition; do not add a catch-all consumer that acknowledges them.
Future dispatch event types require an explicit filter expansion.

### Verification boundary

Focused tests: ManhattanRoutePlannerTest, OrderValidatedListenerTest,
DispatchEventPublisherTest, DispatchMessagingIT and ArtemisBrokerIT. They cover
routes, MDC, real Avro delivery/headers, duplicate receipts and publication,
concurrency, sequence persistence/exhaustion, missing mappings, database rollback,
JMS second-send rollback, pending recovery in a second application context,
database commit failure after broker commit, malformed input and exact listener
attempt counts 1/2/3 before DLQ, and queue filtering. Tests use only disposable
PostgreSQL/Artemis containers and the project broker fixture.

C006 movement/execution deduplication and C009 broader fault/architecture coverage
remain separate. No hosted CI, live queue update, ROS integration or HA is claimed.
