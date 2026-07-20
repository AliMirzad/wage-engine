package com.accounting.security.exception;

/**
 * Thrown when a resource lookup by id / natural key returns no result.
 * Mapped to HTTP 404 by {@link GlobalExceptionHandler}.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException of(String resource, Object id) {
        return new NotFoundException("%s with id %s not found".formatted(resource, id));
    }
}
