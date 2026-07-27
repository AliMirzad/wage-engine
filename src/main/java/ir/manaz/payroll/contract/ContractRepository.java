package ir.manaz.payroll.contract;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByIdAndTenantId(Long id, Long tenantId);

    Page<Contract> findByTenantIdAndEmployeeId(Long tenantId, Long employeeId, Pageable pageable);

    Page<Contract> findByTenantIdAndProjectId(Long tenantId, Long projectId, Pageable pageable);

    boolean existsByTenantIdAndContractNumber(Long tenantId, String contractNumber);

    /**
     * All non-voided contracts for a given (tenant, employee, project) triple.
     * Used by the service layer to detect overlapping ranges before creating a new contract.
     * The list is typically very small (≤ a handful per pair), so in-memory overlap
     * checking is trivial.
     */
    List<Contract> findByTenantIdAndEmployeeIdAndProjectIdAndVoidedFalse(
            Long tenantId, Long employeeId, Long projectId);

    /**
     * Overlap check pushed into SQL, for callers that prefer a boolean answer.
     * Two ranges [a,b] and [c,d] overlap iff a ≤ d AND c ≤ b.
     * A {@code null} end date represents an open-ended range (+∞).
     * <p>
     * When updating an existing row, pass its id as {@code excludeId} to skip it;
     * pass {@code null} when creating a new contract.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM Contract c
            WHERE c.tenantId    = :tenantId
              AND c.employeeId  = :employeeId
              AND c.projectId   = :projectId
              AND c.voided      = false
              AND (:excludeId IS NULL OR c.id <> :excludeId)
              AND (:newEndDate IS NULL OR c.startDate <= :newEndDate)
              AND (c.endDate   IS NULL OR c.endDate   >= :newStartDate)
            """)
    boolean existsOverlapping(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("projectId") Long projectId,
            @Param("newStartDate") LocalDate newStartDate,
            @Param("newEndDate") LocalDate newEndDate,
            @Param("excludeId") Long excludeId);


    @Query("""
        SELECT COUNT(c) FROM Contract c
        WHERE c.tenantId = :tenantId
          AND c.projectId = :projectId
          AND c.voided = false
          AND (c.endDate IS NULL OR c.endDate >= :today)
        """)
    long countActiveByProject(
            @Param("tenantId") Long tenantId,
            @Param("projectId") Long projectId,
            @Param("today") LocalDate today
    );

    @Query("""
        SELECT c.contractNumber FROM Contract c
        WHERE c.tenantId = :tenantId
          AND c.projectId = :projectId
          AND c.voided = false
          AND (c.endDate IS NULL OR c.endDate >= :today)
        ORDER BY c.contractNumber
        """)
    List<String> findActiveContractNumbersByProject(
            @Param("tenantId") Long tenantId,
            @Param("projectId") Long projectId,
            @Param("today") LocalDate today
    );

    @Query("""
    SELECT COUNT(c) FROM Contract c
    WHERE c.tenantId = :tenantId
      AND c.employeeId = :employeeId
      AND c.voided = false
      AND (c.endDate IS NULL OR c.endDate >= :today)
    """)
    long countActiveByEmployee(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("today") LocalDate today
    );

    @Query("""
    SELECT c.contractNumber FROM Contract c
    WHERE c.tenantId = :tenantId
      AND c.employeeId = :employeeId
      AND c.voided = false
      AND (c.endDate IS NULL OR c.endDate >= :today)
    ORDER BY c.contractNumber
    """)
    List<String> findActiveContractNumbersByEmployee(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("today") LocalDate today
    );

    Page<Contract> findByTenantId(Long tenantId, Pageable pageable);

    long countByTenantId(Long tenantId);

    boolean existsByTenantIdAndPreviousContractId(Long tenantId, Long previousContractId);

    @Query(value = """
            SELECT COALESCE(MAX(CAST(SPLIT_PART(contract_number, '-', 3) AS INTEGER)), 0)
            FROM contracts WHERE tenant_id = :tenantId
            """, nativeQuery = true)
    int findMaxContractSequence(@Param("tenantId") Long tenantId);

    /** قراردادهای فعال یک پروژه در تاریخ داده‌شده. */
    @Query("""
            SELECT c FROM Contract c
            WHERE c.tenantId = :tenantId
              AND c.projectId = :projectId
              AND c.voided = false
              AND c.startDate <= :date
              AND (c.endDate IS NULL OR c.endDate >= :date)
            """)
    Page<Contract> findActiveByProject(@Param("tenantId") Long tenantId,
                                       @Param("projectId") Long projectId,
                                       @Param("date") LocalDate date,
                                       Pageable pageable);

    /** همه قراردادهای غیرباطل یک پروژه — شامل خاتمه‌یافته‌ها. */
    Page<Contract> findByTenantIdAndProjectIdAndVoidedFalse(Long tenantId, Long projectId, Pageable pageable);

    /**
     * Contract has no JPA relation to Employee — `employeeId` is a raw column — so
     * the name search uses an ad-hoc LEFT JOIN. LEFT is deliberate: an INNER join
     * would silently drop contracts whose employee is soft-deleted, because
     * Employee carries @SQLRestriction("deleted_at IS NULL").
     *
     * `:search` is CAST because Postgres cannot infer the type of a bare null
     * parameter and fails with `function lower(bytea) does not exist`.
     */
    @Query("""
        SELECT c FROM Contract c
        LEFT JOIN Employee e ON e.id = c.employeeId AND e.tenantId = c.tenantId
        WHERE c.tenantId = :tenantId
          AND (CAST(:search AS String) IS NULL
               OR LOWER(c.contractNumber) LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
               OR LOWER(e.firstName)      LIKE LOWER(CONCAT('%', CAST(:search AS String), '%'))
               OR LOWER(e.lastName)       LIKE LOWER(CONCAT('%', CAST(:search AS String), '%')))
          AND (:status = 'ALL'
               OR (:status = 'VOIDED' AND c.voided = true)
               OR (:status = 'ENDED'  AND c.voided = false
                                      AND c.endDate IS NOT NULL AND c.endDate < CURRENT_DATE)
               OR (:status = 'ACTIVE' AND c.voided = false
                                      AND (c.endDate IS NULL OR c.endDate >= CURRENT_DATE)))
        """)
    Page<Contract> search(@Param("tenantId") Long tenantId,
                          @Param("search") String search,
                          @Param("status") String status,
                          Pageable pageable);
}