package ir.manaz.payroll.contract;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.common.PageResponse;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.payroll.contract.ContractDtos.*;
import ir.manaz.payroll.employee.Employee;
import ir.manaz.payroll.employee.EmployeeRepository;
import ir.manaz.payroll.project.Project;
import ir.manaz.payroll.project.ProjectRepository;
import ir.manaz.security.user.CustomUserDetails;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContractService {

    private final ContractRepository contractRepository;
    private final EmployeeRepository employeeRepository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    private static final int MAX_BACKDATE_YEARS = 5;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<ContractResponse> list(Pageable pageable) {
        Long tenantId = requireTenantId();
        Page<Contract> page = contractRepository.findByTenantId(tenantId, pageable);
        return PageResponse.of(page.map(ContractResponse::from));
    }

    public PageResponse<ContractResponse> listByEmployee(Long employeeId, Pageable pageable) {
        Long tenantId = requireTenantId();
        // اطمینان از وجود کارمند در این tenant
        employeeRepository.findByIdAndTenantId(employeeId, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", employeeId));
        Page<Contract> page = contractRepository.findByTenantIdAndEmployeeId(tenantId, employeeId, pageable);
        return PageResponse.of(page.map(ContractResponse::from));
    }

    public PageResponse<ContractResponse> listByProject(Long projectId, Pageable pageable) {
        Long tenantId = requireTenantId();
        projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new NotFoundException("project.not_found", projectId));
        Page<Contract> page = contractRepository.findByTenantIdAndProjectId(tenantId, projectId, pageable);
        return PageResponse.of(page.map(ContractResponse::from));
    }

    public ContractResponse getById(Long id) {
        Long tenantId = requireTenantId();
        return contractRepository.findByIdAndTenantId(id, tenantId)
                .map(ContractResponse::from)
                .orElseThrow(() -> new NotFoundException("contract.not_found", id));
    }

    // ─── Mutations ───────────────────────────────────────────

    @Transactional
    public ContractResponse create(CreateContractRequest req) {
        Long tenantId = requireTenantId();

        // 1. اعتبارسنجی کارمند و پروژه در همین tenant
        Employee employee = employeeRepository.findByIdAndTenantId(req.employeeId(), tenantId)
                .orElseThrow(() -> new NotFoundException("contract.employee.not_found"));
        if (!employee.isActive() || employee.getDeletedAt() != null) {
            throw new BusinessException("contract.employee.inactive");
        }

        Project project = projectRepository.findByIdAndTenantId(req.projectId(), tenantId)
                .orElseThrow(() -> new NotFoundException("contract.project.not_found"));
        if (!project.isActive()) {
            throw new BusinessException("contract.project.archived");
        }

        // 2. اعتبارسنجی تاریخ‌ها
        validateDates(req.startDate(), req.endDate());

        // 3. previousContractId اگه ارسال شده
        if (req.previousContractId() != null) {
            validatePreviousContract(tenantId, req.previousContractId());
        }

        // 4. overlap check
        List<Contract> existing = contractRepository
                .findByTenantIdAndEmployeeIdAndProjectIdAndVoidedFalse(
                        tenantId, req.employeeId(), req.projectId());

        List<String> overlappingNumbers = existing.stream()
                .filter(c -> rangesOverlap(
                        c.getStartDate(), c.getEndDate(),
                        req.startDate(), req.endDate()))
                .map(Contract::getContractNumber)
                .sorted()
                .toList();

        if (!overlappingNumbers.isEmpty()) {
            throw new ConflictException(
                    "contract.overlap",
                    String.join("، ", overlappingNumbers)
            );
        }

        // 5. تولید شماره قرارداد
        String contractNumber = generateContractNumber(tenantId);

        Contract contract = Contract.builder()
                .tenantId(tenantId)
                .employeeId(req.employeeId())
                .projectId(req.projectId())
                .contractNumber(contractNumber)
                .baseSalary(req.baseSalary())
                .housingAllowance(nullSafe(req.housingAllowance()))
                .foodAllowance(nullSafe(req.foodAllowance()))
                .childAllowanceBase(nullSafe(req.childAllowanceBase()))
                .currency(req.currency() != null ? req.currency() : "IRR")
                .startDate(req.startDate())
                .endDate(req.endDate())
                .previousContractId(req.previousContractId())
                .terms(req.terms())
                .notes(req.notes())
                .voided(false)
                .build();
        contract = contractRepository.save(contract);

        audit(AuditEvent.CONTRACT_CREATED, contract,
                "contractNumber=" + contractNumber
                        + (req.previousContractId() != null ? " previousId=" + req.previousContractId() : ""));

        return ContractResponse.from(contract);
    }

    @Transactional
    public ContractResponse update(Long id, UpdateContractRequest req) {
        Long tenantId = requireTenantId();
        Contract contract = contractRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("contract.not_found", id));

        contract.setNotes(req.notes());
        contract.setTerms(req.terms());

        audit(AuditEvent.CONTRACT_UPDATED, contract, null);
        return ContractResponse.from(contract);
    }

    @Transactional
    public void end(Long id, EndContractRequest req) {
        Long tenantId = requireTenantId();
        Contract contract = contractRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("contract.not_found", id));

        if (contract.isVoided()) {
            throw new BusinessException("contract.already_voided");
        }
        if (contract.getEndDate() != null && !contract.getEndDate().isAfter(LocalDate.now())) {
            throw new BusinessException("contract.already_ended", contract.getEndDate());
        }
        if (req.endDate().isBefore(contract.getStartDate())) {
            throw new BusinessException("contract.end_date.before_start", contract.getStartDate());
        }

        contract.setEndDate(req.endDate());
        audit(AuditEvent.CONTRACT_ENDED, contract, "endDate=" + req.endDate());
    }

    @Transactional
    public void voidContract(Long id, VoidContractRequest req) {
        Long tenantId = requireTenantId();
        Contract contract = contractRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("contract.not_found", id));

        if (contract.isVoided()) {
            throw new BusinessException("contract.already_voided");
        }

        contract.setVoided(true);
        contract.setVoidedAt(Instant.now());
        contract.setVoidedBy(currentUserId());
        contract.setVoidReason(req.reason());

        audit(AuditEvent.CONTRACT_VOIDED, contract, "reason=" + req.reason());
    }

    // ─── Helpers ─────────────────────────────────────────────

    private void validateDates(LocalDate start, LocalDate end) {
        if (end != null && end.isBefore(start)) {
            throw new BusinessException("contract.dates.invalid");
        }
        if (start.isBefore(LocalDate.now().minusYears(MAX_BACKDATE_YEARS))) {
            throw new BusinessException("contract.start_date.past_limit");
        }
    }

    private void validatePreviousContract(Long tenantId, Long previousId) {
        Contract previous = contractRepository.findByIdAndTenantId(previousId, tenantId)
                .orElseThrow(() -> new NotFoundException("contract.previous.not_found", previousId));

        // قرارداد قبلی باید end شده باشد (endDate تعریف شده و در آینده نباشد)
        if (previous.getEndDate() == null || previous.getEndDate().isAfter(LocalDate.now())) {
            throw new BusinessException("contract.previous.must_be_ended");
        }

        // یک قرارداد قبلی نباید دو successor داشته باشه
        if (contractRepository.existsByTenantIdAndPreviousContractId(tenantId, previousId)) {
            throw new ConflictException("contract.previous.already_linked", previousId);
        }
    }

    /**
     * تولید شماره قرارداد: CT-{tenantId}-{4-digit-seq}
     * TODO(deferred #5): race-prone؛ UNIQUE constraint در DB safety-net است.
     */
    private String generateContractNumber(Long tenantId) {
        long next = contractRepository.countByTenantId(tenantId) + 1;
        return String.format("CT-%d-%04d", tenantId, next);
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("contract.tenant_required");
        }
        return tenantId;
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getId();
        }
        return null;
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    private void audit(String event, Contract c, String details) {
        auditLogService.log(
                event,
                AuditOutcome.SUCCESS,
                c.getTenantId(),
                currentUserId(),
                currentUsername(),
                details,
                "CONTRACT",
                String.valueOf(c.getId())
        );
    }

    private static boolean rangesOverlap(LocalDate aStart, LocalDate aEnd,
                                         LocalDate bStart, LocalDate bEnd) {
        boolean aStartLeBEnd = (bEnd == null) || !aStart.isAfter(bEnd);
        boolean bStartLeAEnd = (aEnd == null) || !bStart.isAfter(aEnd);
        return aStartLeBEnd && bStartLeAEnd;
    }
}