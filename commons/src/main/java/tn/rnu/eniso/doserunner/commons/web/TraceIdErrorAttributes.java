package tn.rnu.eniso.doserunner.commons.web;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;

import java.util.Map;

/**
 * Errors that never reach a controller (unknown path, filter failures, Spring Security 401/403)
 * are rendered by Boot's {@code /error} endpoint, not by {@link GlobalExceptionHandler}.
 * This adds the same {@code traceId} field to that body.
 */
public class TraceIdErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attrs = super.getErrorAttributes(webRequest, options);
        Object traceId = webRequest.getAttribute(TraceHeaders.MDC_TRACE_ID, RequestAttributes.SCOPE_REQUEST);
        if (traceId != null) {
            attrs.put("traceId", traceId);
        }
        return attrs;
    }
}
