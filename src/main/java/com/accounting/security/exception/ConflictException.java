package com.accounting.security.exception;

/**
 * Thrown when a request violates a state or uniqueness constraint —
 * e.g. duplicate national ID, overlapping active contract.
 * Mapped to HTTP 409 by {@link GlobalExceptionHandler}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
