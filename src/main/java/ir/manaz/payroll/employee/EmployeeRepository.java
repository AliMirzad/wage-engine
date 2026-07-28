package ir.manaz.payroll.employee;

import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import java.util.Collection;

import java.util.List;
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

    List<Employee> findByTenantIdAndIdIn(Long tenantId, Collection<Long> ids);

    @Query("""
        SELECT e FROM Employee e
        WHERE e.tenantId = :tenantId
          AND (CAST(:active AS Boolean) IS NULL OR e.active = :active)
          AND (:includeTerminated = true OR e.terminationDate IS NULL)
          AND (CAST(:search AS String) IS NULL
               OR LOWER(e.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
               OR LOWER(e.lastName)  LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
               OR e.personnelCode    LIKE CONCAT('%', CAST(:search AS String), '%')
               OR e.nationalId       LIKE CONCAT('%', CAST(:search AS String), '%'))
        """)
    Page<Employee> search(@Param("tenantId") Long tenantId,
                          @Param("active") Boolean active,
                          @Param("includeTerminated") boolean includeTerminated,
                          @Param("search") String search,
                          Pageable pageable);
}
