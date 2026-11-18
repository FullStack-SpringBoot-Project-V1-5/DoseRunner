package tn.rnu.eniso.doserunner.contracts.messaging;

/**
 * Artemis address and queue names, LOCKED at tag v0.1-contracts.
 * deploy/artemis/broker.xml (owner C) must declare exactly these.
 *
 * <p>Multicast addresses get one queue per consumer service; a service subscribes through the
 * fully-qualified queue name {@code address::queue} (see {@link #fqqn(String, String)}).
 */
public final class Addresses {

    /** MULTICAST. All saga events (events/*.avsc). One queue per consuming service. */
    public static final String EVENTS = "doserunner.events";

    /** ANYCAST. MissionCommand. Grouped by robot (JMSXGroupID = robotId), consumers select on robotId. */
    public static final String ROBOT_COMMANDS = "robot.commands";

    /** MULTICAST. RobotTelemetry from every robot. */
    public static final String ROBOT_TELEMETRY = "robot.telemetry";

    /** ANYCAST. Messages that failed max-delivery-attempts (3) land here; shown on the fleet board. */
    public static final String DLQ = "DLQ";

    /** ANYCAST. Expired messages. */
    public static final String EXPIRY_QUEUE = "ExpiryQueue";

    // ---- per-service queues on the multicast addresses (queue name = service name) ----
    public static final String QUEUE_ORDER_SERVICE = "order-service";
    public static final String QUEUE_DISPATCH_SERVICE = "dispatch-service";
    public static final String QUEUE_CUSTODY_SERVICE = "custody-service";

    /** Fully-qualified queue name, e.g. {@code doserunner.events::dispatch-service}. */
    public static String fqqn(String address, String queue) {
        return address + "::" + queue;
    }

    private Addresses() {
    }
}
