package tn.rnu.eniso.doserunner.commons.dto;

import java.time.Instant;
import java.util.List;

/**
 * The single error body returned by every DoseRunner REST endpoint.
 *
 * @param timestamp   when the error was produced
 * @param status      HTTP status code
 * @param error       HTTP reason phrase (e.g. "Bad Request")
 * @param message     human-readable explanation, safe to show to the client
 * @param path        request URI
 * @param traceId     the correlation id, so a user report can be matched to the logs
 * @param fieldErrors validation problems, empty when not a validation error
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        String traceId,
        List<FieldError> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public record FieldError(String field, String message) {
    }
}
