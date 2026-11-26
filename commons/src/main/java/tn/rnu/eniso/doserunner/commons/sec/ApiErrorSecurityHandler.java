package tn.rnu.eniso.doserunner.commons.sec;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import tn.rnu.eniso.doserunner.commons.dto.ApiError;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

/**
 * Writes 401 and 403 as {@link ApiError} bodies (with the trace id) instead of Spring Security's empty responses,
 * so a security error looks like every other DoseRunner error.
 */
public class ApiErrorSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorSecurityHandler.class);

    private final JsonMapper json;

    public ApiErrorSecurityHandler(JsonMapper json) {
        this.json = json;
    }

    /** 401: no token, or an invalid / expired one. */
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res, AuthenticationException ex)
            throws IOException {
        log.info("401 on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        res.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        write(req, res, HttpStatus.UNAUTHORIZED, "Missing or invalid bearer token");
    }

    /** 403: valid token, but the role does not allow this route. */
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex) throws IOException {
        log.info("403 on {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        write(req, res, HttpStatus.FORBIDDEN, "Access denied");
    }

    private void write(HttpServletRequest req, HttpServletResponse res, HttpStatus status, String message)
            throws IOException {
        if (res.isCommitted()) {
            return;
        }
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), MDC.get(TraceHeaders.MDC_TRACE_ID), List.of());
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(res.getOutputStream(), body);
    }
}
