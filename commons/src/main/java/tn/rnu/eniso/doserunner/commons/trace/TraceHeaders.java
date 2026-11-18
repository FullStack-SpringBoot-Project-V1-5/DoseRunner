package tn.rnu.eniso.doserunner.commons.trace;

/** Header / MDC / message-property names shared by HTTP, gRPC and Artemis code paths. */
public final class TraceHeaders {

    public static final String TRACEPARENT = "traceparent";
    public static final String REQUEST_ID = "x-request-id";
    /** MDC key; referenced by logback-spring.xml. */
    public static final String MDC_TRACE_ID = "traceId";
    /** JMS property used to carry the trace id across Artemis (days 3-7, owner B/C). */
    public static final String JMS_TRACE_ID = "traceId";

    private TraceHeaders() {
    }
}
