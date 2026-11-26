package tn.rnu.eniso.doserunner.contracts.messaging;

/**
 * JMS message properties every DoseRunner message carries. The body is always a {@code BytesMessage}
 * holding Avro single-object encoding (2-byte marker + CRC-64-AVRO fingerprint of the writer schema + payload).
 */
public final class MessageHeaders {

    /** Simple name of the Avro record, e.g. "OrderValidated". Lets consumers filter with a selector. */
    public static final String EVENT_TYPE = "eventType";

    /** Correlation id, copied from the MDC (commons TraceHeaders.MDC_TRACE_ID) when publishing. */
    public static final String TRACE_ID = "traceId";

    /** Saga key, also put in the consumer's MDC as "orderId". */
    public static final String ORDER_ID = "orderId";

    /** Target robot of a MissionCommand; robots consume with selector {@code robotId = '<own id>'}. */
    public static final String ROBOT_ID = "robotId";

    /** Standard JMS grouping property (Artemis message groups): set to robotId on robot.commands. */
    public static final String GROUP_ID = "JMSXGroupID";

    /** Artemis scheduled delivery (epoch millis), used for DispatchTimedOut. */
    public static final String SCHEDULED_DELIVERY_TIME = "_AMQ_SCHED_DELIVERY";

    private MessageHeaders() {
    }
}
