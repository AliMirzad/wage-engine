package ir.manaz.exception;

/**
 * Thrown when a request violates a state or uniqueness constraint —
 * e.g. duplicate national ID, overlapping active contract.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends BusinessException {
    public ConflictException(String messageKey, Object... args) {
        super(messageKey, args);
    }
}
