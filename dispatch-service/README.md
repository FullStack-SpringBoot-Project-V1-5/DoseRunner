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
