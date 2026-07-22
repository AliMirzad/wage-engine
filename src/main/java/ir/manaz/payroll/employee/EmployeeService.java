package ir.manaz.payroll.employee;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.common.PageResponse;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.payroll.contract.ContractRepository;
import ir.manaz.payroll.employee.EmployeeDtos.CreateEmployeeRequest;
import ir.manaz.payroll.employee.EmployeeDtos.EmployeeResponse;
import ir.manaz.payroll.employee.EmployeeDtos.UpdateEmployeeRequest;
import ir.manaz.security.user.CustomUserDetails;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final AuditLogService auditLogService;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<EmployeeResponse> list(Pageable pageable) {
        Long tenantId = requireTenantId();
        Page<Employee> page = employeeRepository.findByTenantId(tenantId, pageable);
        return PageResponse.of(page.map(EmployeeResponse::from));
    }

    public EmployeeResponse getById(Long id) {
        Long tenantId = requireTenantId();
        return employeeRepository.findByIdAndTenantId(id, tenantId)
                .map(EmployeeResponse::from)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));
    }

    // ─── Mutations ───────────────────────────────────────────

    @Transactional
    public EmployeeResponse create(CreateEmployeeRequest req) {
        Long tenantId = requireTenantId();

        validateDates(req.birthDate(), req.hireDate());

        if (employeeRepository.existsByTenantIdAndNationalId(tenantId, req.nationalId())) {
            throw new ConflictException("employee.national_id.duplicate", req.nationalId());
        }

        String personnelCode = generatePersonnelCode(tenantId);

        Employee employee = Employee.builder()
                .tenantId(tenantId)
                .personnelCode(personnelCode)
                .firstName(req.firstName())
                .lastName(req.lastName())
                .nationalId(req.nationalId())
                .birthDate(req.birthDate())
                .hireDate(req.hireDate())
                .phoneNumber(req.phoneNumber())
                .email(req.email())
                .childrenCount(req.childrenCount() != null ? req.childrenCount() : 0)
                .iban(req.iban())
                .active(true)
                .build();
        employee = employeeRepository.save(employee);

        audit(AuditEvent.EMPLOYEE_CREATED, employee, "personnelCode=" + personnelCode);
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public EmployeeResponse update(Long id, UpdateEmployeeRequest req) {
        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));

        validateDates(req.birthDate(), req.hireDate());

        employee.setFirstName(req.firstName());
        employee.setLastName(req.lastName());
        employee.setBirthDate(req.birthDate());
        employee.setHireDate(req.hireDate());
        employee.setPhoneNumber(req.phoneNumber());
        employee.setEmail(req.email());
        if (req.childrenCount() != null) {
            employee.setChildrenCount(req.childrenCount());
        }
        employee.setIban(req.iban());

        audit(AuditEvent.EMPLOYEE_UPDATED, employee, null);
        return EmployeeResponse.from(employee);
    }

    @Transactional
    public void deactivate(Long id) {
        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));

        if (!employee.isActive()) {
            throw new BusinessException("employee.already_inactive");
        }

        employee.setActive(false);
        audit(AuditEvent.EMPLOYEE_DEACTIVATED, employee, null);
    }

    @Transactional
    public void reactivate(Long id) {
        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));

        if (employee.isActive()) {
            throw new BusinessException("employee.already_active");
        }

        employee.setActive(true);
        audit(AuditEvent.EMPLOYEE_REACTIVATED, employee, null);
    }

    @Transactional
    public void delete(Long id) {
        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));

        // مسدود اگه قرارداد فعال داره
        List<String> activeContracts = contractRepository
                .findActiveContractNumbersByEmployee(tenantId, employee.getId(), LocalDate.now());

        if (!activeContracts.isEmpty()) {
            String numbers = String.join("، ", activeContracts);
            throw new ConflictException(
                    "employee.delete.has_active_contracts",
                    activeContracts.size(),
                    numbers
            );
        }

        employee.setDeletedAt(Instant.now());
        employee.setDeletedBy(currentUserId());
        employee.setActive(false);

        audit(AuditEvent.EMPLOYEE_DELETED, employee, "personnelCode=" + employee.getPersonnelCode());
    }

    // ─── Helpers ─────────────────────────────────────────────

    /**
     * تولید کد پرسنلی: EMP-{tenantId}-{4-digit-seq}
     * الگوریتم: count(*) + 1 → pad به ۴ رقم.
     * TODO(deferred #5): race-prone در concurrency بالا؛ UNIQUE constraint در DB safety-net است.
     */
    private String generatePersonnelCode(Long tenantId) {
        long next = employeeRepository.countByTenantId(tenantId) + 1;
        return String.format("EMP-%d-%04d", tenantId, next);
    }

    private void validateDates(LocalDate birthDate, LocalDate hireDate) {
        LocalDate today = LocalDate.now();
        if (birthDate.isAfter(today)) {
            throw new BusinessException("employee.birth_date.future");
        }
        if (hireDate.isAfter(today)) {
            throw new BusinessException("employee.hire_date.future");
        }
        if (hireDate.isBefore(birthDate)) {
            throw new BusinessException("employee.hire_date.before_birth");
        }
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            // TODO(deferred #17): مسیر SUPER_ADMIN
            throw new BusinessException("employee.tenant_required");
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

    private void audit(String event, Employee employee, String details) {
        auditLogService.log(
                event,
                AuditOutcome.SUCCESS,
                employee.getTenantId(),
                currentUserId(),
                currentUsername(),
                details,
                "EMPLOYEE",
                String.valueOf(employee.getId())
        );
    }
}