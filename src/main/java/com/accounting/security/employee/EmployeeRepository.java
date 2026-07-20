package com.accounting.security.employee;

import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Employee repository.
 * <p>
 * All standard finders honor the {@code @SQLRestriction("deleted_at IS NULL")}
 * on {@link Employee}, so soft-deleted rows are hidden automatically.
 * <p>
 * Use {@link #findByIdIncludingDeleted(Long, Long)} only from admin / audit code paths.
 */
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Employee> findByTenantIdAndNationalId(Long tenantId, String nationalId);

    Optional<Employee> findByTenantIdAndPersonnelCode(Long tenantId, String personnelCode);

    boolean existsByTenantIdAndNationalId(Long tenantId, String nationalId);

    boolean existsByTenantIdAndPersonnelCode(Long tenantId, String personnelCode);

    Page<Employee> findByTenantId(Long tenantId, Pageable pageable);

    long countByTenantId(Long tenantId);

    /**
     * Native query that bypasses the {@code @SQLRestriction} filter, for audit/legal use only.
     * Returns the row even if {@code deleted_at IS NOT NULL}.
     */
    @Query(value = """
            SELECT * FROM employees
            WHERE id = :id AND tenant_id = :tenantId
            """, nativeQuery = true)
    @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
    Optional<Employee> findByIdIncludingDeleted(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
