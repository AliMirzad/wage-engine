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
import ir.manaz.security.jwt.AuthenticatedPrincipal;
import ir.manaz.payroll.employee.EmployeeDtos.TerminateEmployeeRequest;
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

import static ir.manaz.payroll.employee.EmployeeDtos.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final ContractRepository contractRepository;
    private final AuditLogService auditLogService;

    // ─── Queries ─────────────────────────────────────────────

    public PageResponse<EmployeeResponse> list(Boolean active, boolean includeTerminated,
                                               String search, Pageable pageable) {
        Long tenantId = requireTenantId();
        String q = (search == null || search.isBlank()) ? null : search.trim();
        return PageResponse.of(
                employeeRepository.search(tenantId, active, includeTerminated, q, pageable)
                        .map(EmployeeResponse::from));
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
                .firstName(req.firstName().trim())
                .lastName(req.lastName().trim())
                .nationalId(req.nationalId())
                .birthDate(req.birthDate())
                .hireDate(req.hireDate())
                .phoneNumber(req.phoneNumber())
                .email(req.email())
                .childrenCount(req.childrenCount() != null ? req.childrenCount() : 0)
                .iban(req.iban())
                .bankName(req.bankName())
                .maritalStatus(req.maritalStatus() != null ? req.maritalStatus() : MaritalStatus.SINGLE)
                .insuranceNumber(req.insuranceNumber())
                .insuranceType(req.insuranceType() != null ? req.insuranceType() : InsuranceType.MANDATORY)
                .employmentType(req.employmentType() != null ? req.employmentType() : EmploymentType.FULL_TIME)
                .jobTitle(req.jobTitle())
                .fatherName(req.fatherName())
                .idCardNumber(req.idCardNumber())
                .issuePlace(req.issuePlace())
                .militaryStatus(req.militaryStatus())
                .educationLevel(req.educationLevel())
                .address(req.address())
                .emergencyContactName(req.emergencyContactName())
                .emergencyContactPhone(req.emergencyContactPhone())
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

        if (req.firstName() != null && !req.firstName().isBlank()) employee.setFirstName(req.firstName().trim());
        if (req.lastName() != null && !req.lastName().isBlank()) employee.setLastName(req.lastName().trim());
        if (req.phoneNumber() != null) employee.setPhoneNumber(req.phoneNumber());
        if (req.email() != null) employee.setEmail(req.email());
        if (req.childrenCount() != null) employee.setChildrenCount(req.childrenCount());
        if (req.iban() != null) employee.setIban(req.iban());
        if (req.bankName() != null) employee.setBankName(req.bankName());

        if (req.maritalStatus() != null) employee.setMaritalStatus(req.maritalStatus());
        if (req.insuranceNumber() != null) employee.setInsuranceNumber(req.insuranceNumber());
        if (req.insuranceType() != null) employee.setInsuranceType(req.insuranceType());
        if (req.employmentType() != null) employee.setEmploymentType(req.employmentType());
        if (req.jobTitle() != null) employee.setJobTitle(req.jobTitle());

        if (req.fatherName() != null) employee.setFatherName(req.fatherName());
        if (req.idCardNumber() != null) employee.setIdCardNumber(req.idCardNumber());
        if (req.issuePlace() != null) employee.setIssuePlace(req.issuePlace());
        if (req.militaryStatus() != null) employee.setMilitaryStatus(req.militaryStatus());
        if (req.educationLevel() != null) employee.setEducationLevel(req.educationLevel());
        if (req.address() != null) employee.setAddress(req.address());
        if (req.emergencyContactName() != null) employee.setEmergencyContactName(req.emergencyContactName());
        if (req.emergencyContactPhone() != null) employee.setEmergencyContactPhone(req.emergencyContactPhone());

        LocalDate birth = req.birthDate() != null ? req.birthDate() : employee.getBirthDate();
        LocalDate hire = req.hireDate() != null ? req.hireDate() : employee.getHireDate();
        validateDates(birth, hire);
        employee.setBirthDate(birth);
        employee.setHireDate(hire);

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

        if (employee.getTerminationDate() != null) {
            throw new ConflictException("employee.terminated.use_rehire",
                    employee.getPersonnelCode(), employee.getTerminationDate());
        }

        employee.setActive(true);
        audit(AuditEvent.EMPLOYEE_REACTIVATED, employee, null);
    }

    /**
     * ثبت ترک کار — متفاوت با حذف نرم.
     * رکورد باقی می‌ماند (برای محاسبه سنوات و تسویه) ولی کارمند غیرفعال می‌شود.
     * در صورت وجود قرارداد فعال بلاک می‌شود؛ ابتدا باید قراردادها خاتمه یابند.
     */
    @Transactional
    public EmployeeResponse terminate(Long id, TerminateEmployeeRequest req) {
        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));

        if (employee.getTerminationDate() != null) {
            throw new ConflictException("employee.already_terminated",
                    employee.getPersonnelCode(), employee.getTerminationDate());
        }
        if (req.terminationDate().isBefore(employee.getHireDate())) {
            throw new BusinessException("employee.termination_date.before_hire", employee.getHireDate());
        }

        List<String> activeContracts = contractRepository
                .findActiveContractNumbersByEmployee(tenantId, employee.getId(), LocalDate.now());
        if (!activeContracts.isEmpty()) {
            throw new ConflictException("employee.terminate.has_active_contracts",
                    activeContracts.size(), String.join("، ", activeContracts));
        }

        employee.setTerminationDate(req.terminationDate());
        employee.setTerminationReason(req.terminationReason());
        employee.setActive(false);

        audit(AuditEvent.EMPLOYEE_TERMINATED, employee,
                "terminationDate=" + req.terminationDate() + " reason=" + req.terminationReason());
        return EmployeeResponse.from(employee);
    }

    /**
     * استخدام مجدد کارمندی که ترک کار کرده است.
     * <p>
     * رکورد جدید ساخته نمی‌شود چون سابقه بیمه به شخص وصل است نه به رکورد،
     * و قاعده یکتایی کد ملی هم اجازه رکورد دوم نمی‌دهد.
     * تاریخ استخدام با مقدار جدید جایگزین می‌شود — طبق قانون کار، با تسویه
     * سنوات هنگام ترک کار سابقه صفر می‌شود. تاریخ قبلی در audit log می‌ماند.
     */
    @Transactional
    public EmployeeResponse rehire(Long id, RehireEmployeeRequest req) {
        Long tenantId = requireTenantId();
        Employee employee = employeeRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("employee.not_found", id));

        if (employee.getTerminationDate() == null) {
            throw new ConflictException("employee.not_terminated", employee.getPersonnelCode());
        }
        if (req.hireDate().isBefore(employee.getTerminationDate())) {
            throw new BusinessException("employee.rehire_date.before_termination",
                    employee.getTerminationDate());
        }
        if (req.hireDate().isAfter(LocalDate.now())) {
            throw new BusinessException("employee.hire_date.future");
        }

        LocalDate previousHire = employee.getHireDate();
        LocalDate previousTermination = employee.getTerminationDate();

        employee.setHireDate(req.hireDate());
        employee.setTerminationDate(null);
        employee.setTerminationReason(null);
        employee.setActive(true);

        audit(AuditEvent.EMPLOYEE_REHIRED, employee,
                "previousHire=" + previousHire
                        + " previousTermination=" + previousTermination
                        + " newHire=" + req.hireDate()
                        + (req.note() != null ? " note=" + req.note() : ""));

        return EmployeeResponse.from(employee);
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
        int next = employeeRepository.findMaxPersonnelSequence(tenantId) + 1;
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