package com.accounting.security.tenant;

/**
 * Marker interface for entities that belong to a specific tenant (company).
 * Ensures data isolation between different companies using the SaaS.
 */
public interface TenantAware {
    Long getTenantId();
    void setTenantId(Long tenantId);
}
