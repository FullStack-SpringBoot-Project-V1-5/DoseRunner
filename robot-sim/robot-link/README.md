# RobotLink — C007 handoff for Knani

Spring-free Jakarta JMS transport for one robot. It receives frozen `MissionCommand`
records from `robot.commands` with an exact `robotId` selector and publishes frozen
`RobotTelemetry` v1 on the multicast `robot.telemetry` address. There is no ROS,
Spring, database, movement engine, scheduler, or robot execution state in this library.

This standalone Maven build lives under Gueddes's `robot-sim/` ownership but is
independent of the Boot application and its Java 25 dependencies. It is **not in
the root reactor**; run both verification commands below. The simulator does not
load these artifacts. Do not run a simulator and a ROS executor owning the same
robot IDs: they would compete for the same anycast commands.

## Build and verify (repository root, JDK 25)

```sh
export JAVA_HOME=/run/current-system/sw/lib/openjdk
export MAVEN_USER_HOME=/tmp/doserunner-maven
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository \
  -f robot-sim/robot-link/pom.xml clean verify
rtk proxy ./mvnw -B -ntp -Dmaven.repo.local=/tmp/doserunner-maven/repository verify
```

Both production artifacts use `javac --release 11`. JUnit 6/Testcontainers test
code uses Java 25 and is excluded from the handoff. The integration test runs the
packaged jars in `eclipse-temurin:11-jre` against a disposable Artemis 2.44.0 broker
with the unchanged project XML. Docker access and image downloads are required;
there is no shared-broker URL override or PostgreSQL requirement.

Outputs:

- `contracts-java11/target/robot-contracts-java11-0.1.0-SNAPSHOT.jar`: generated
  `MissionCommand`, `RobotTelemetry`, `GridPoint`, builders, and unchanged shared
  `Addresses`, `MessageHeaders`, `AvroTrust` classes.
- `client/target/robot-link-0.1.0-SNAPSHOT.jar`: API, JMS implementation, example.
- `client/target/lib/*.jar`: the complete runtime dependency set, including the
  generated-contract jar. No Spring or test libraries.

To reproduce the handoff jars without tests (after a successful verify), run the
same command with `clean package -Dmaven.test.skip=true`. Clean only removes this
standalone build's target outputs. The fixed `project.build.outputTimestamp`,
pinned plugins/dependencies and identical JDK/Maven inputs give identical jar
SHA-256 values across clean builds, including the compiled executable example.
Compare both production jars and `client/target/lib/*.jar` with `sha256sum`.
Keep the entire lib directory; no install into the core reactor is needed.

Schemas are generated directly from the repository's frozen files; no schema
copies or edits. Shared convention source files are copied unchanged into target
and compiled for Java 11. Generated Avro `fromByteBuffer`/`toByteBuffer` APIs use
single-object encoding (C3 01 + schema fingerprint + payload), as does the core
shared codec. No second Avro codec is implemented. Cross-codec tests in the core
simulator build and frozen-schema fingerprint tests verify compatibility.

Avro remains 1.12.2. This independent parent pins Artemis **client 2.38.0**, the
Java 11-compatible line; the core's 2.53.0 client contains Java 17 bytecode and
cannot be handed to Java 11. The broker remains 2.44.0. The full handoff's base
classes are checked for bytecode version <=55 and the Java 11 probe exercises
receive, rollback/redelivery, publication and commit. Multi-release jar entries
for newer JVMs are ignored by Java 11. This legacy client pin must be revisited
when Knani can upgrade the runtime; no global dependency versions are changed.
See [Artemis upgrade notes](https://artemis.apache.org/components/artemis/documentation/latest/versions.html#upgrading-from-2-38-0)
for the Java 17 transition and compatibility with older clients.

## API and acknowledgment

Package: `tn.rnu.eniso.doserunner.robotlink`. [Public API v1](API.md) is frozen
after independent re-review APPROVED on 2026-11-23; C007 is passed under Wassim's
owner approval. ROSJava integration remains pending under Knani's responsibility.

```java
try (ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
     RobotLink link = new JmsRobotLink(factory, "R-01", user, password)) {
    Optional<CommandDelivery> received = link.receive(1000);
    if (received.isPresent()) {
        CommandDelivery delivery = received.get();
        // Validate execution policy; deduplicate missionId + seq + payload in the application.
        // Persist/perform acceptance as required by the actual controller before acknowledging.
        link.acknowledge(delivery);
        // On execution/pose progress, using the retained delivery's trace/correlation metadata:
        link.publishTelemetry(new RobotTelemetry("R-01", 1, 0, 99), delivery);
    }
}
```

Use **one caller thread per link**. Marshal ROS pose callbacks to that thread;
`receive` requires a positive finite timeout. Only `close` may run on another
thread (including a shutdown hook). The caller owns the supplied connection
factory. The link owns and closes its connection, sessions, consumer and producer.

`receive` returns an optional `CommandDelivery` exposing `command()`, `orderId()`,
`traceId()`, `correlationId()` and `deliveryCount()`. The command is a mutable
Avro-generated record; retain the original payload for execution deduplication.
One unsettled delivery at a time. `acknowledge(delivery)` commits only the command
session; `rollback(delivery)` requests redelivery. A JMSException from either
settlement retires/closes the link; reconnect and reconcile instead of retrying
settlement with the old token. Tokens cannot settle another
link or an older delivery. Metadata used for telemetry is captured before the
caller can mutate the command. An acknowledged delivery remains usable as telemetry
context on its original link. Closing an unsettled link rolls the transaction back.

`publishTelemetry(record, delivery)` uses a **separate local transaction** and a
persistent BytesMessage. It commits the telemetry send without acknowledging the
command. It validates robot ownership, nonnegative position and battery 0..100,
and sets eventType, robotId, orderId, optional traceId, and JMSCorrelationID from
the delivery. Body contains only robotId/x/y/battery. Failures roll back the send;
the caller chooses retry/backoff. If a failure-path rollback itself fails, the link
closes and rejects further use so a later commit cannot acknowledge uncertain work. No automatic reconnection/recovery is promised.

Selected malformed messages automatically roll back and throw; unchanged C004
settings move them to DLQ after three unsuccessful deliveries. Caller code should
catch validation errors and continue polling, or reconnect after transport failures.
Validation includes BytesMessage/single-object decoding, event/body/header target,
canonical UUID shape (case-insensitive), positive seq, nonblank compartment,
nonnegative timestamp/cells, <=10000 waypoints and optional order/group consistency.
Bodies are bounded to 1 MiB. Target IDs allow 1..64 ASCII letters/digits/_/- (no
selector injection). Different IDs stay queued for their owners. Route adjacency,
pharmacy convention, execution capacity and sequence/replay policy belong to the
controller, not this transport. Do not acknowledge a rejected/conflicting command.

## Runnable synthetic example (Java 11+)

Copy `client/target/robot-link-0.1.0-SNAPSHOT.jar` and **all** of `client/target/lib/`
to Knani's machine, preserving the `lib` directory. Include this README, API.md and source
for the example. Do not add the core Java 25 `contracts` jar to the classpath: it
has the same generated class names but incompatible bytecode. `javax.jms` clients
are not interchangeable with this `jakarta.jms` API.

From the copied directory:

```sh
export ARTEMIS_USER=your-user
export ARTEMIS_PASSWORD=your-password
java -cp 'robot-link-0.1.0-SNAPSHOT.jar:lib/*' \
  tn.rnu.eniso.doserunner.robotlink.RobotLinkExample \
  'tcp://localhost:61616?callTimeout=2000&reconnectAttempts=0' R-01
```

The example waits up to 30 seconds for one selected command, prints mission/robot,
publishes its last waypoint with synthetic battery 100, and commits the command.
An empty route is simply acknowledged. This is a transport demonstration, with no
movement timing or physical control; it can emit duplicate telemetry on replay.
For Gradle, include both the link jar and the complete dependency directory via
`implementation files(...)` / `implementation fileTree(dir: ..., include: ['*.jar'])`.
Keep all dependency licenses/metadata included in the jars when handing them over.

## Reliability and acceptance boundary

At-least-once delivery remains visible to the caller. The transport deliberately
does not suppress repeated commands: only the execution owner can know whether
it durably accepted/executed them. It must identify exact missionId/seq/payload
replays as no-ops, reject conflicting/stale execution, and choose durable history
or explicitly accept a volatile restart limitation. There is no exactly-once or
crash-safe execution guarantee. Acknowledging before durable acceptance can lose
work after a crash. Executing before acknowledgment can execute twice on redelivery.
Separate telemetry/command commits are not atomic; ambiguous sends/commits may
produce duplicate telemetry. On transport/commit uncertainty, close/reconnect and
reconcile using the application's execution record before attempting movement.
Do not keep a command transaction open for a long physical mission without an
explicit controller/recovery design.

The live Week 1 Notion page was checked on 2026-11-23: it specifies pure JMS,
`--release 11` generated classes and handoff, without method signatures or a
javax/jakarta requirement. Wassim initially authorized the proposal and then, as
C007 owner, explicitly established it as the integration contract on 2026-11-23.
Independent re-review passed; [API v1](API.md) is frozen and C007 is complete.
Knani's interface confirmation is not required. **ROSJava integration remains pending
under Knani's responsibility.** No message has been sent to Knani; no receipt or
working ROSJava integration is claimed. Turtlesim control and telemetry v2 are
outside C007.

## Freeze verification — 2026-11-23

Final standalone `clean verify`: **7 unit + 18 integration/runtime = 25 passed**,
zero failures/errors/skips. This includes the actual shipped RobotLinkExample on
Java 11 and commit/rollback failure regressions against isolated Artemis. Initial
review found stale-token settlement after a failed commit/rollback; the link now
retires on either failure. Both regressions failed before the fix and passed after
it. Independent re-review approved the final implementation and signatures.

Two clean builds with the same Java 25.0.4/Maven 3.9.16 inputs produced identical
SHA-256 values for both production jars and every runtime dependency (39 paths;
the generated-contract jar is also copied in lib, giving 38 unique jars):

| Artifact | SHA-256 |
|---|---|
| robot-contracts-java11-0.1.0-SNAPSHOT.jar | `4070024868007f8e7e30658c03e3ed0583407a65f5248e80a96f839c2a94880f` |
| robot-link-0.1.0-SNAPSHOT.jar | `3c97d6a57f8abb824260be53ab29be43ae70e8b5e682bd1790fa9dc2cc6bd1cc` |

Retained prior root verification: 158 passed (139 Surefire + 19 Failsafe), including
the two shared-codec compatibility tests. The root was not rerun for the standalone
settlement/packaging changes; it does not build or load RobotLink. No hosted CI or
ROSJava execution is claimed. Original C007 verification evidence remains in the
tracker and progress history; the later owner decision supersedes the earlier
external-confirmation completion gate.
