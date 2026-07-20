package ir.manaz.exception;

/**
 * Thrown when a resource lookup by id / natural key returns no result.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends BusinessException {
    public NotFoundException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}