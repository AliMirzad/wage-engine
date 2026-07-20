package com.accounting.security.common;

import java.time.Instant;

/**
 * Marker interface for entities that support soft deletion.
 * <p>
 * Entities annotated with {@code @SQLRestriction("deleted_at IS NULL")} are auto-filtered
 * by Hibernate. To include soft-deleted rows use a native query or a dedicated
 * repository method (e.g. for auditing).
 */
public interface SoftDeletable {

    Instant getDeletedAt();

    void setDeletedAt(Instant deletedAt);

    Long getDeletedBy();

    void setDeletedBy(Long deletedBy);

    default boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
