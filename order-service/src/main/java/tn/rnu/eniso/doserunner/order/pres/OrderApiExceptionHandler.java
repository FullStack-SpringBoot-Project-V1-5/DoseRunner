package tn.rnu.eniso.doserunner.order.pres;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tn.rnu.eniso.doserunner.commons.dto.ApiError;
import tn.rnu.eniso.doserunner.commons.trace.TraceHeaders;
import tn.rnu.eniso.doserunner.order.shared.error.OrderServiceException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice(annotations = RestController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(OrderApiExceptionHandler.class);

    @ExceptionHandler(OrderServiceException.class)
    public ResponseEntity<ApiError> onDomainError(OrderServiceException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.kind()) {
            case INVALID -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
        };
        log.info("{} {} rejected with {}: {}", request.getMethod(), request.getRequestURI(), status.value(),
                ex.getMessage());
        return build(status, ex.getMessage(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> onConcurrentUpdate(ObjectOptimisticLockingFailureException ex,
                                                       HttpServletRequest request) {
        log.warn("Concurrent update on {} {}", request.getMethod(), request.getRequestURI());
        return build(HttpStatus.CONFLICT, "The resource was modified concurrently, reload and retry", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                   HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value for parameter '" + ex.getName() + "'", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), MDC.get(TraceHeaders.MDC_TRACE_ID), List.of());
        return ResponseEntity.status(status).body(body);
    }
}
