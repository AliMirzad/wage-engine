package ir.manaz.common;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,        // machine-readable: e.g. "employee.not_found"
        String message,     // human-readable Persian
        String path
) {
    public static ErrorResponse of(int status, String error, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path);
    }
}