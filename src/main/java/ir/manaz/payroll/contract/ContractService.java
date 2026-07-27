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
import ir.manaz.security.jwt.AuthenticatedPrincipal;
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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

    public PageResponse<ContractResponse> list(String search, String status, Pageable pageable) {
        Long tenantId = requireTenantId();
        String term = (search == null || search.isBlank()) ? null : search.trim();
        String state = (status == null || status.isBlank()) ? "ALL" : status.trim().toUpperCase();

        if (!Set.of("ALL", "ACTIVE", "ENDED", "VOIDED").contains(state)) {
            throw new BusinessException("contract.status.invalid");
        }

        return PageResponse.of(
                contractRepository.search(tenantId, term, state, pageable)
                        .map(ContractResponse::from));
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

    /**
     * کارگران یک پروژه — یعنی کسانی که روی آن قرارداد دارند.
     * <p>
     * کارمند مستقیماً به پروژه وصل نیست؛ اتصال از راه قرارداد است.
     * صفحه‌بندی روی قرارداد است نه کارمند: در حالت پیش‌فرض هر کارمند
     * حداکثر یک ردیف دارد (قاعده عدم تداخل تضمینش می‌کند)، و در حالت
     * includeFormer هر ردیف یک دوره همکاری است.
     */
    public PageResponse<ProjectEmployeeResponse> listEmployeesByProject(Long projectId,
                                                                        boolean includeFormer,
                                                                        Pageable pageable) {
        Long tenantId = requireTenantId();
        projectRepository.findByIdAndTenantId(projectId, tenantId)
                .orElseThrow(() -> new NotFoundException("project.not_found", projectId));

        Page<Contract> contracts = includeFormer
                ? contractRepository.findByTenantIdAndProjectIdAndVoidedFalse(tenantId, projectId, pageable)
                : contractRepository.findActiveByProject(tenantId, projectId, LocalDate.now(), pageable);

        if (contracts.isEmpty()) {
            return PageResponse.of(Page.empty(pageable));
        }

        List<Long> employeeIds = contracts.getContent().stream()
                .map(Contract::getEmployeeId).distinct().toList();

        // یک کوئری برای همه کارمندان صفحه — نه N کوئری
        Map<Long, Employee> byId = employeeRepository
                .findByTenantIdAndIdIn(tenantId, employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));

        return PageResponse.of(contracts.map(c -> {
            Employee e = byId.get(c.getEmployeeId());
            // کارمند حذف‌شده ولی قرارداد باقی‌مانده — فقط در حالت includeFormer ممکن است،
            // چون delete در حضور قرارداد فعال مسدود می‌شود. ردیف حذف نمی‌شود تا
            // شمارش صفحه‌بندی درست بماند و قرارداد از دید حسابدار پنهان نشود.
            return e != null
                    ? ProjectEmployeeResponse.of(c, e)
                    : ProjectEmployeeResponse.deletedEmployee(c);
        }));
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
        if (employee.getTerminationDate() != null) {
            throw new BusinessException("contract.employee.terminated",
                    employee.getPersonnelCode(), employee.getTerminationDate());
        }
        if (req.startDate().isBefore(employee.getHireDate())) {
            throw new BusinessException("contract.start_date.before_hire",
                    employee.getPersonnelCode(), employee.getHireDate());
        }

        Project project = projectRepository.findByIdAndTenantId(req.projectId(), tenantId)
                .orElseThrow(() -> new NotFoundException("contract.project.not_found"));

        if (!project.getStatus().allowsNewContracts()) {
            throw new BusinessException("contract.project.not_accepting",
                    project.getCode(), project.getStatus().name());
        }

        // 2. اعتبارسنجی تاریخ‌ها
        validateDates(req.startDate(), req.endDate());

        validateContractType(req.contractType(), req.endDate(), req.workingHoursPerWeek());

        if (req.startDate().isBefore(project.getStartDate())) {
            throw new BusinessException("contract.start_date.before_project",
                    project.getCode(), project.getStartDate());
        }
        if (project.getEndDate() != null && req.startDate().isAfter(project.getEndDate())) {
            throw new BusinessException("contract.start_date.after_project_end",
                    project.getCode(), project.getEndDate());
        }

        // 3. previousContractId اگه ارسال شده
        if (req.previousContractId() != null) {
            validatePreviousContract(tenantId, req.previousContractId(),
                    req.employeeId(), req.projectId());
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
                .contractType(req.contractType() != null ? req.contractType() : ContractType.TEMPORARY)
                .salaryBasis(req.salaryBasis() != null ? req.salaryBasis() : SalaryBasis.MONTHLY)
                .baseSalary(req.baseSalary())
                .housingAllowance(nullSafe(req.housingAllowance()))
                .foodAllowance(nullSafe(req.foodAllowance()))
                .childAllowanceBase(nullSafe(req.childAllowanceBase()))
                .transportAllowance(nullSafe(req.transportAllowance()))
                .seniorityPay(nullSafe(req.seniorityPay()))
                .hardshipAllowance(nullSafe(req.hardshipAllowance()))
                .workingHoursPerWeek(req.workingHoursPerWeek() != null
                        ? req.workingHoursPerWeek() : new BigDecimal("44"))
                .currency(req.currency() != null ? req.currency() : "IRR")
                .startDate(req.startDate())
                .endDate(req.endDate())
                .signedDate(req.signedDate())
                .probationEndDate(req.probationEndDate())
                .jobTitle(req.jobTitle())
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

        // فیلدهای مالی و تاریخ‌ها تغییرناپذیرند — برای تغییر، قرارداد را خاتمه
        // داده و قرارداد جدید با previousContractId بسازید (الزام حقوقی)
        if (req.notes() != null) contract.setNotes(req.notes());
        if (req.terms() != null) contract.setTerms(req.terms());
        if (req.jobTitle() != null) contract.setJobTitle(req.jobTitle());

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

    /**
     * زنجیره previousContractId فقط برای ادامه همان همکاری معنا دارد:
     * همان کارمند، همان پروژه، قرارداد قبلی خاتمه‌یافته و غیرباطل،
     * و بدون جانشین دیگر. بدون این قیود، زنجیره در گزارش سنوات
     * و سوابق بیمه بی‌معنا می‌شود.
     */
    private void validatePreviousContract(Long tenantId, Long previousId,
                                          Long employeeId, Long projectId) {
        Contract previous = contractRepository.findByIdAndTenantId(previousId, tenantId)
                .orElseThrow(() -> new NotFoundException("contract.previous.not_found", previousId));

        if (!previous.getEmployeeId().equals(employeeId)
                || !previous.getProjectId().equals(projectId)) {
            throw new BusinessException("contract.previous.different_pair",
                    previous.getContractNumber());
        }
        if (previous.isVoided()) {
            throw new BusinessException("contract.previous.voided", previous.getContractNumber());
        }
        if (previous.getEndDate() == null) {
            throw new BusinessException("contract.previous.must_be_ended");
        }
        if (contractRepository.existsByTenantIdAndPreviousContractId(tenantId, previousId)) {
            throw new ConflictException("contract.previous.already_linked",
                    previous.getContractNumber());
        }
    }

    /**
     * تولید شماره قرارداد: CT-{tenantId}-{4-digit-seq}
     * TODO(deferred #5): race-prone؛ UNIQUE constraint در DB safety-net است.
     */
    private String generateContractNumber(Long tenantId) {
        int next = contractRepository.findMaxContractSequence(tenantId) + 1;
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

    private AuthenticatedPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p) ? p : null;
    }

    private Long currentUserId() {
        AuthenticatedPrincipal p = principal();
        return p == null ? null : p.userId();
    }

    private String currentUsername() {
        AuthenticatedPrincipal p = principal();
        return p == null ? null : p.username();
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

    /**
     * قواعد قانون کار:
     * قرارداد موقت و آزمایشی باید تاریخ پایان داشته باشند؛ دائم نباید داشته باشد.
     * ساعات کار هفتگی نمی‌تواند از سقف قانونی ۴۴ ساعت بیشتر باشد.
     */
    private void validateContractType(ContractType type, LocalDate endDate, BigDecimal weeklyHours) {
        ContractType effective = type != null ? type : ContractType.TEMPORARY;

        if (effective.requiresEndDate() && endDate == null) {
            throw new BusinessException("contract.end_date.required_for_type", effective.name());
        }
        if (effective == ContractType.PERMANENT && endDate != null) {
            throw new BusinessException("contract.end_date.not_allowed_for_permanent");
        }
        if (weeklyHours != null) {
            if (weeklyHours.signum() <= 0) {
                throw new BusinessException("contract.working_hours.invalid");
            }
            if (weeklyHours.compareTo(LEGAL_MAX_WEEKLY_HOURS) > 0) {
                throw new BusinessException("contract.working_hours.exceeds_legal_max",
                        LEGAL_MAX_WEEKLY_HOURS);
            }
        }
    }

    private static final BigDecimal LEGAL_MAX_WEEKLY_HOURS = new BigDecimal("44");
}