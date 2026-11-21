# Dispatch service — C001/C002

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
  schemas. Transition rules, route/command fields, assignment concurrency and
  event deduplication remain for their owning features. There is no unique
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

C002 does not wire a facade, reserve robots, persist assignments, publish events,
or implement the optional v2 plugin. C003 must supply current snapshots, the
actual pharmacy point and a concurrency policy within its transaction. The
`OrderValidated.controlled` field remains unchanged; additional compartment
eligibility rules have not been specified or implemented by this v1 selector.
