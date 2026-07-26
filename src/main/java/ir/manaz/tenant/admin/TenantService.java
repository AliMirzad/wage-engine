package ir.manaz.tenant.admin;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.common.PageResponse;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.security.role.DefaultRoles;
import ir.manaz.security.role.Role;
import ir.manaz.security.role.RoleRepository;
import ir.manaz.security.user.User;
import ir.manaz.security.user.UserRepository;
import ir.manaz.tenant.Tenant;
import ir.manaz.tenant.TenantRepository;
import ir.manaz.tenant.mycompany.MyCompanyDtos;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ir.manaz.exception.BusinessException;
import ir.manaz.tenant.TenantContext;
import static ir.manaz.tenant.mycompany.MyCompanyDtos.*;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import static ir.manaz.tenant.admin.TenantDtos.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PageResponse<TenantResponse> list(Pageable pageable) {
        return PageResponse.of(tenantRepository.findAll(pageable).map(t -> toResponse(t, null, null)));
    }

    @Transactional(readOnly = true)
    public TenantResponse getById(Long id) {
        return toResponse(load(id), null, null);
    }

    /**
     * شرکت و اولین کاربر مدیر آن را در یک تراکنش می‌سازد.
     * اگر ساخت کاربر شکست بخورد، شرکت هم rollback می‌شود — هرگز شرکتی بدون مدیر نمی‌ماند.
     */
    @Transactional
    public TenantResponse create(CreateTenantRequest req, Long actorUserId, String actorUsername) {
        String code = req.code().trim().toLowerCase();
        if (tenantRepository.existsByCode(code)) {
            throw new ConflictException("tenant.code.duplicate", code);
        }

        String username = req.adminUsername().trim().toLowerCase();
        String email = req.adminEmail().trim().toLowerCase();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new ConflictException("user.username.duplicate");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("user.email.duplicate");
        }

        Role companyAdmin = roleRepository.findByNameAndTenantIdIsNull(DefaultRoles.COMPANY_ADMIN)
                .orElseThrow(() -> new IllegalStateException("COMPANY_ADMIN role missing"));

        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name(req.name().trim())
                .code(code)
                .nationalId(req.nationalId())
                .insuranceWorkshopCode(req.insuranceWorkshopCode())
                .economicCode(req.economicCode())
                .iban(req.iban())
                .address(req.address())
                .phone(req.phone())
                .active(true)
                .build());

        Set<Role> roles = new HashSet<>();
        roles.add(companyAdmin);

        User admin = userRepository.save(User.builder()
                .tenantId(tenant.getId())
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.adminPassword()))
                .firstName(req.adminFirstName())
                .lastName(req.adminLastName())
                .enabled(true)
                .accountNonLocked(true)
                .passwordChangedAt(Instant.now())
                .roles(roles)
                .build());

        auditLogService.log(AuditEvent.TENANT_CREATED, AuditOutcome.SUCCESS,
                tenant.getId(), actorUserId, actorUsername,
                "Tenant '" + tenant.getCode() + "' created with admin '" + admin.getUsername() + "'");
        log.info("Tenant {} created by {} with admin {}", tenant.getCode(), actorUsername, admin.getUsername());

        return toResponse(tenant, admin.getId(), admin.getUsername());
    }

    @Transactional
    public TenantResponse update(Long id, UpdateTenantRequest req, Long actorUserId, String actorUsername) {
        Tenant tenant = load(id);

        if (req.name() != null && !req.name().isBlank()) tenant.setName(req.name().trim());
        if (req.nationalId() != null) tenant.setNationalId(req.nationalId());
        if (req.insuranceWorkshopCode() != null) tenant.setInsuranceWorkshopCode(req.insuranceWorkshopCode());
        if (req.economicCode() != null) tenant.setEconomicCode(req.economicCode());
        if (req.iban() != null) tenant.setIban(req.iban());
        if (req.address() != null) tenant.setAddress(req.address());
        if (req.phone() != null) tenant.setPhone(req.phone());

        tenant = tenantRepository.save(tenant);
        auditLogService.log(AuditEvent.TENANT_UPDATED, AuditOutcome.SUCCESS,
                tenant.getId(), actorUserId, actorUsername, "Tenant '" + tenant.getCode() + "' updated");
        return toResponse(tenant, null, null);
    }

    /** غیرفعال کردن شرکت — همه‌ی کاربرانش بلافاصله از ورود محروم می‌شوند (چک در AuthService.login). */
    @Transactional
    public void deactivate(Long id, Long actorUserId, String actorUsername) {
        Tenant tenant = load(id);
        if (!tenant.isActive()) throw new ConflictException("tenant.already_inactive", tenant.getCode());
        tenant.setActive(false);
        tenantRepository.save(tenant);
        auditLogService.log(AuditEvent.TENANT_DEACTIVATED, AuditOutcome.SUCCESS,
                tenant.getId(), actorUserId, actorUsername, "Tenant '" + tenant.getCode() + "' deactivated");
        log.warn("Tenant {} deactivated by {}", tenant.getCode(), actorUsername);
    }

    @Transactional
    public void activate(Long id, Long actorUserId, String actorUsername) {
        Tenant tenant = load(id);
        if (tenant.isActive()) throw new ConflictException("tenant.already_active", tenant.getCode());
        tenant.setActive(true);
        tenantRepository.save(tenant);
        auditLogService.log(AuditEvent.TENANT_ACTIVATED, AuditOutcome.SUCCESS,
                tenant.getId(), actorUserId, actorUsername, "Tenant '" + tenant.getCode() + "' activated");
    }

    // ====================== MY COMPANY (tenant-scoped) ======================

    @Transactional(readOnly = true)
    public MyCompanyDtos.MyCompanyResponse getMyCompany() {
        return toMyCompany(load(requireTenantId()));
    }

    @Transactional
    public MyCompanyResponse updateMyCompany(UpdateMyCompanyRequest req,
                                             Long actorUserId, String actorUsername) {
        Tenant tenant = load(requireTenantId());

        if (req.name() != null && !req.name().isBlank()) tenant.setName(req.name().trim());
        if (req.nationalId() != null) tenant.setNationalId(req.nationalId());
        if (req.insuranceWorkshopCode() != null) tenant.setInsuranceWorkshopCode(req.insuranceWorkshopCode());
        if (req.economicCode() != null) tenant.setEconomicCode(req.economicCode());
        if (req.iban() != null) tenant.setIban(req.iban());
        if (req.address() != null) tenant.setAddress(req.address());
        if (req.phone() != null) tenant.setPhone(req.phone());

        tenant = tenantRepository.save(tenant);
        auditLogService.log(AuditEvent.TENANT_UPDATED, AuditOutcome.SUCCESS,
                tenant.getId(), actorUserId, actorUsername,
                "Tenant '" + tenant.getCode() + "' updated by its own admin");
        return toMyCompany(tenant);
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("tenant.tenant_required");
        return tenantId;
    }

    private MyCompanyResponse toMyCompany(Tenant t) {
        return new MyCompanyResponse(
                t.getName(), t.getCode(), t.getNationalId(),
                t.getInsuranceWorkshopCode(), t.getEconomicCode(), t.getIban(),
                t.getAddress(), t.getPhone()
        );
    }

    private Tenant load(Long id) {
        return tenantRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("tenant.not_found", id));
    }

    private TenantResponse toResponse(Tenant t, Long adminUserId, String adminUsername) {
        return new TenantResponse(
                t.getId(), t.getName(), t.getCode(), t.getNationalId(),
                t.getInsuranceWorkshopCode(), t.getEconomicCode(), t.getIban(),
                t.getAddress(), t.getPhone(), t.isActive(), t.getCreatedAt(),
                adminUserId, adminUsername
        );
    }
}