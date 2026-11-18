package tn.rnu.eniso.doserunner.commons.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tn.rnu.eniso.doserunner.commons.dto.ApiError;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;

import java.time.Instant;
import java.util.List;

/**
 * Turns exceptions thrown by REST controllers into {@link ApiError} bodies.
 * Thymeleaf {@code @Controller} pages are not affected (this advice targets {@code @RestController} only).
 */
@RestControllerAdvice(annotations = org.springframework.web.bind.annotation.RestController.class)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onInvalidBody(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldError> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", req, fields);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> onIllegalArgument(IllegalArgumentException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception ex, HttpServletRequest req) throws Exception {
        // Let Spring Security's ExceptionTranslationFilter produce 401/403 itself.
        // (commons must not depend on spring-security, hence the name check.)
        if (ex.getClass().getName().startsWith("org.springframework.security.")) {
            throw ex;
        }
        // ResponseStatusException, NoResourceFoundException, HttpRequestMethodNotSupportedException, ...
        if (ex instanceof ErrorResponse er) {
            String detail = er.getBody().getDetail();
            return build(er.getStatusCode(), detail != null ? detail : ex.getMessage(), req, List.of());
        }
        log.error("Unhandled exception on {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", req, List.of());
    }

    private static ResponseEntity<ApiError> build(HttpStatusCode status, String message, HttpServletRequest req,
                                                  List<ApiError.FieldError> fields) {
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String reason = resolved != null ? resolved.getReasonPhrase() : String.valueOf(status.value());
        ApiError body = new ApiError(Instant.now(), status.value(), reason, message, req.getRequestURI(),
                MDC.get(TraceHeaders.MDC_TRACE_ID), fields);
        return ResponseEntity.status(status).body(body);
    }
}
