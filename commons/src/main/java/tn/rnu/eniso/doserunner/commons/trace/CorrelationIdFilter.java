package tn.rnu.eniso.doserunner.commons.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gives every incoming request a trace id and makes it visible everywhere:
 * <ol>
 *   <li>W3C {@code traceparent} header (set by Istio/Envoy later) → its 32-hex trace-id</li>
 *   <li>otherwise {@code x-request-id} (if it is a safe token)</li>
 *   <li>otherwise a freshly generated 32-hex id</li>
 * </ol>
 * The id goes into the SLF4J MDC under {@value TraceHeaders#MDC_TRACE_ID} (so every log line carries it)
 * and is echoed back in the {@value TraceHeaders#REQUEST_ID} response header.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    /** version-traceid-parentid-flags, lowercase hex only (W3C Trace Context §3.2). */
    private static final Pattern TRACEPARENT =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");
    private static final String INVALID_TRACE_ID = "0".repeat(32);
    /** Accept caller-supplied ids only if they cannot inject anything into logs/headers. */
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = resolveTraceId(request.getHeader(TraceHeaders.TRACEPARENT),
                request.getHeader(TraceHeaders.REQUEST_ID));
        MDC.put(TraceHeaders.MDC_TRACE_ID, traceId);
        // also as a request attribute: it survives into the /error dispatch, after the MDC is cleared
        request.setAttribute(TraceHeaders.MDC_TRACE_ID, traceId);
        response.setHeader(TraceHeaders.REQUEST_ID, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(TraceHeaders.MDC_TRACE_ID);
        }
    }

    static String resolveTraceId(String traceparent, String requestId) {
        if (traceparent != null) {
            Matcher m = TRACEPARENT.matcher(traceparent.trim());
            if (m.matches() && !INVALID_TRACE_ID.equals(m.group(1))) {
                return m.group(1);
            }
        }
        if (requestId != null && SAFE_REQUEST_ID.matcher(requestId.trim()).matches()) {
            return requestId.trim();
        }
        return newTraceId();
    }

    /** 32 lowercase hex chars: same shape as a W3C trace-id, so it can be propagated as-is. */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
