# RobotLink public API v1

Owner: Wassim Gueddes (C007). Decision date: 2026-11-23.
Freeze status: **FROZEN — independent re-review APPROVED, 2026-11-23.**

The review reproduced and resolved failed-settlement token reuse; both regressions
and the packaged example passed in the final 25-test standalone verification.

This is the owner-approved integration contract for Knani's future ROSJava adapter.
Knani's implementation or interface confirmation is not a C007 completion gate.
ROSJava integration remains pending under Knani's responsibility. Artifact version
is currently `0.1.0-SNAPSHOT`; API v1 identifies this frozen surface, not an Avro
schema change or an assertion that the artifact has been published.

Package: `tn.rnu.eniso.doserunner.robotlink`. Java 11+, Jakarta JMS, no Spring.

Public signatures below omit implementation bodies; they are an API inventory.

```java
public interface RobotLink extends AutoCloseable {
    Optional<CommandDelivery> receive(long timeoutMillis) throws JMSException, IOException;
    void acknowledge(CommandDelivery delivery) throws JMSException;
    void rollback(CommandDelivery delivery) throws JMSException;
    void publishTelemetry(RobotTelemetry telemetry, CommandDelivery context) throws JMSException, IOException;
    void close() throws JMSException;
}

public final class JmsRobotLink implements RobotLink {
    public JmsRobotLink(ConnectionFactory factory, String robotId, String user, String password) throws JMSException;
    // Implements exactly the five methods above.
}

public final class CommandDelivery {
    public MissionCommand command();
    public String orderId();
    public String traceId();
    public String correlationId();
    public int deliveryCount();
    // No public constructor: delivery tokens are issued by RobotLink.
}
```

Types: `ConnectionFactory`/`JMSException` are `jakarta.jms`;
`IOException` is `java.io`, `Optional` is `java.util`; records are the frozen
`tn.rnu.eniso.doserunner.contracts.robot` generated Avro types.
`RobotLinkExample.main(String[]) throws Exception` is the executable synthetic
example; its command-line arguments are broker URL then robotId, with credentials
from ARTEMIS_USER/ARTEMIS_PASSWORD. It is not a motor controller.

## Behavioral contract

- Construct one link per owned robot, using an exact robotId selector. IDs allow
  1..64 ASCII letters/digits/underscore/hyphen. Never run competing executors for
  the same ID. JMSXGroupID alone does not provide targeting.
- Use one caller thread per link; only close may be called from another thread.
  The caller owns the factory. The link owns/closes its connection, two sessions,
  consumer and producer; close interrupts blocking receipt and rolls back an
  unsettled command transaction.
- receive requires a positive finite millisecond timeout. Empty optional means no
  command arrived. At most one delivery may remain unsettled; another receive
  fails with IllegalStateException. Validated commands are not acknowledged by
  receive. Malformed selected messages roll back and throw IOException (or
  JMSException on JMS/property access failures); unchanged broker retry/DLQ policy
  applies. Validation/limits are detailed in README.md.
- acknowledge commits the command transaction. rollback rolls it back. Both
  require the exact current token issued by that link; null, another link's token
  or a stale token raises IllegalArgumentException. **Either settlement throwing
  JMSException retires/closes the link**; further operations fail with
  IllegalStateException, and cleanup failures are suppressed on the original
  exception. Reconnect and reconcile; never retry settlement on the old token.
- CommandDelivery exposes a mutable generated MissionCommand. Metadata is captured
  from the original delivery before the caller can mutate it; nullable traceId and
  correlationId are preserved. deliveryCount is JMSXDeliveryCount. Retain the
  original command payload/missionId/seq for application execution deduplication.
- publishTelemetry uses an independent local JMS transaction, persistent
  RobotTelemetry v1 BytesMessage and the delivery's captured order/trace/correlation
  properties. It does not settle the command. Context must originate from the
  same link and remains usable after acknowledgment. Robot ID must match the
  link, coordinates must be nonnegative and battery must be 0..100; invalid values
  raise IllegalArgumentException (null inputs raise NullPointerException).
  Send/commit failure triggers rollback; failure of that rollback retires the link.
  The caller chooses retry/backoff and reconciliation for ambiguous sends.
- Closed links reject all operations other than repeated close. Execution,
  route/pharmacy policy, capacity, replay/conflict decisions and durable recovery
  belong to the controller. No exactly-once, crash-safe execution, automatic
  recovery or atomic telemetry/command commit is provided.

## Compatibility and changes

Future incompatible changes to these signatures, threading, settlement or metadata
semantics require explicit C007 owner approval and an API version change, plus
coordination with the ROSJava adapter owner once integration begins. Do not replace
Jakarta with javax.jms or add Spring to this contract. Frozen Avro contracts remain
under their existing shared change-control process.

The Java 11 handoff includes robot-link, robot-contracts-java11 and all runtime jars
in client/target/lib. Do not mix the core Java 25 contracts jar into that classpath.
The standalone build pins its legacy Java 11 Artemis client independently of the
core applications. See README.md for reproducible build and executable commands.
