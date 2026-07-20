package com.accounting.security.contract;

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
     * Two ranges [a,b] and [c,d] overlap iff a ≤ d AND c ≤ b, treating null end
     * dates as +∞.
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
              AND c.startDate   <= COALESCE(:newEndDate, DATE '9999-12-31')
              AND COALESCE(c.endDate, DATE '9999-12-31') >= :newStartDate
            """)
    boolean existsOverlapping(
            @Param("tenantId") Long tenantId,
            @Param("employeeId") Long employeeId,
            @Param("projectId") Long projectId,
            @Param("newStartDate") LocalDate newStartDate,
            @Param("newEndDate") LocalDate newEndDate,
            @Param("excludeId") Long excludeId);
}
