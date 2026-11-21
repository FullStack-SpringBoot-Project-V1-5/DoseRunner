# Dispatch persistence — C001

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
