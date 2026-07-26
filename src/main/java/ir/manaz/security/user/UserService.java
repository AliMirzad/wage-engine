package ir.manaz.security.user;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.common.PageResponse;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.security.role.DefaultRoles;
import ir.manaz.security.role.Permission;
import ir.manaz.security.role.Role;
import ir.manaz.security.role.RoleRepository;
import ir.manaz.security.token.RefreshTokenService;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static ir.manaz.security.user.UserDtos.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final RefreshTokenService refreshTokenService;

    /** نقش‌های سیستمی که مدیر شرکت اجازه تخصیص دارد. SUPER_ADMIN و COMPANY_ADMIN عمداً نیستند. */
    private static final Set<String> ASSIGNABLE_SYSTEM_ROLES = Set.of(
            DefaultRoles.ACCOUNTANT, DefaultRoles.MANAGER,
            DefaultRoles.EMPLOYEE, DefaultRoles.AUDITOR
    );

    /** دسترسی‌هایی که تخصیص‌شان توسط مدیر شرکت به ارتقای دسترسی منجر می‌شود. */
    private static final Set<Permission> ESCALATION_PERMISSIONS = EnumSet.of(
            Permission.TENANT_READ, Permission.TENANT_WRITE, Permission.TENANT_DELETE,
            Permission.ROLE_WRITE
    );

    private static final String PASSWORD_ALPHABET =
            "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    // ============================== READ ==============================

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Pageable pageable) {
        Long tenantId = requireTenantId();
        return PageResponse.of(userRepository.findByTenantId(tenantId, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        return toResponse(load(id));
    }

    // ============================== CREATE ==============================

    @Transactional
    public CreateUserResponse create(CreateUserRequest req, Long actorUserId, String actorUsername) {
        Long tenantId = requireTenantId();

        String username = req.username().trim().toLowerCase();
        String email = req.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username))
            throw new ConflictException("user.username.duplicate");
        if (userRepository.existsByEmailIgnoreCase(email))
            throw new ConflictException("user.email.duplicate");

        Set<Role> roles = resolveAssignableRoles(req.roleNames());

        String rawPassword = generatePassword();

        User user = userRepository.save(User.builder()
                .tenantId(tenantId)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .enabled(true)
                .accountNonLocked(true)
                .passwordChangedAt(Instant.now())
                .roles(roles)
                .build());

        auditLogService.log(AuditEvent.USER_CREATED, AuditOutcome.SUCCESS,
                tenantId, actorUserId, actorUsername,
                "User '" + username + "' created with roles=["
                        + roles.stream().map(Role::getName).sorted().collect(Collectors.joining(",")) + "]");
        log.info("User {} created in tenant {} by {}", username, tenantId, actorUsername);

        return new CreateUserResponse(toResponse(user), rawPassword);
    }

    // ============================== UPDATE ==============================

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, Long actorUserId, String actorUsername) {
        User user = load(id);
        guardNotSelf(user, actorUserId, "user.cannot_modify_self");

        if (req.email() != null && !req.email().isBlank()) {
            String email = req.email().trim().toLowerCase();
            if (!email.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("user.email.duplicate");
            }
            user.setEmail(email);
        }
        if (req.firstName() != null) user.setFirstName(req.firstName());
        if (req.lastName() != null) user.setLastName(req.lastName());

        if (req.roleNames() != null) {
            if (req.roleNames().isEmpty()) throw new BusinessException("user.roles.required");
            user.setRoles(resolveAssignableRoles(req.roleNames()));
            auditLogService.log(AuditEvent.USER_ROLE_CHANGED, AuditOutcome.SUCCESS,
                    user.getTenantId(), actorUserId, actorUsername,
                    "Roles of '" + user.getUsername() + "' changed to ["
                            + String.join(",", req.roleNames()) + "]");
        }

        user = userRepository.save(user);
        auditLogService.log(AuditEvent.USER_UPDATED, AuditOutcome.SUCCESS,
                user.getTenantId(), actorUserId, actorUsername,
                "User '" + user.getUsername() + "' updated");
        return toResponse(user);
    }

    // ============================== ENABLE / DISABLE ==============================

    @Transactional
    public void deactivate(Long id, Long actorUserId, String actorUsername) {
        User user = load(id);
        guardNotSelf(user, actorUserId, "user.cannot_deactivate_self");
        if (!user.isEnabled()) throw new ConflictException("user.already_disabled", user.getUsername());

        user.setEnabled(false);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.USER_UPDATED, AuditOutcome.SUCCESS,
                user.getTenantId(), actorUserId, actorUsername,
                "User '" + user.getUsername() + "' deactivated");
        log.warn("User {} deactivated by {}", user.getUsername(), actorUsername);
    }

    @Transactional
    public void activate(Long id, Long actorUserId, String actorUsername) {
        User user = load(id);
        if (user.isEnabled()) throw new ConflictException("user.already_enabled", user.getUsername());

        user.setEnabled(true);
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        userRepository.save(user);

        auditLogService.log(AuditEvent.USER_UPDATED, AuditOutcome.SUCCESS,
                user.getTenantId(), actorUserId, actorUsername,
                "User '" + user.getUsername() + "' activated");
    }

    // ============================== RESET PASSWORD ==============================

    @Transactional
    public String resetPassword(Long id, Long actorUserId, String actorUsername) {
        User user = load(id);
        String rawPassword = generatePassword();

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.PASSWORD_RESET_COMPLETE, AuditOutcome.SUCCESS,
                user.getTenantId(), actorUserId, actorUsername,
                "Password of '" + user.getUsername() + "' reset by admin");
        log.warn("Password of {} reset by admin {}", user.getUsername(), actorUsername);

        return rawPassword;
    }

    // ============ ADMIN (cross-tenant, SUPER_ADMIN only) ============

    /** برای مسیر ادمین: همه‌ی نقش‌ها جز SUPER_ADMIN قابل تخصیص‌اند — از جمله COMPANY_ADMIN. */
    private static final Set<String> ADMIN_BLOCKED_ROLES = Set.of(DefaultRoles.SUPER_ADMIN);

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> listForTenant(Long tenantId, Pageable pageable) {
        return PageResponse.of(userRepository.findByTenantId(tenantId, pageable).map(this::toResponse));
    }

    @Transactional
    public CreateUserResponse createForTenant(Long tenantId, CreateUserRequest req,
                                              Long actorUserId, String actorUsername) {
        String username = req.username().trim().toLowerCase();
        String email = req.email().trim().toLowerCase();

        if (userRepository.existsByUsernameIgnoreCase(username))
            throw new ConflictException("user.username.duplicate");
        if (userRepository.existsByEmailIgnoreCase(email))
            throw new ConflictException("user.email.duplicate");

        Set<Role> roles = resolveAdminAssignableRoles(req.roleNames());
        String rawPassword = generatePassword();

        User user = userRepository.save(User.builder()
                .tenantId(tenantId)
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .firstName(req.firstName())
                .lastName(req.lastName())
                .enabled(true)
                .accountNonLocked(true)
                .passwordChangedAt(Instant.now())
                .roles(roles)
                .build());

        auditLogService.log(AuditEvent.USER_CREATED, AuditOutcome.SUCCESS,
                tenantId, actorUserId, actorUsername,
                "User '" + username + "' created in tenant " + tenantId + " by platform admin with roles=["
                        + roles.stream().map(Role::getName).sorted().collect(Collectors.joining(",")) + "]");
        log.warn("Platform admin {} created user {} in tenant {}", actorUsername, username, tenantId);

        return new CreateUserResponse(toResponse(user), rawPassword);
    }

    /** بازنشانی رمز کاربر یک شرکت — مسیر نجات وقتی تنها مدیر شرکت دسترسی‌اش را از دست داده. */
    @Transactional
    public String resetPasswordForTenant(Long tenantId, Long userId,
                                         Long actorUserId, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("user.not_found", userId));

        String rawPassword = generatePassword();
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setFailedLoginAttempts(0);
        user.setAccountNonLocked(true);
        user.setLockedUntil(null);
        user.setEnabled(true);
        userRepository.save(user);
        refreshTokenService.revokeAllForUser(user.getId());

        auditLogService.log(AuditEvent.PASSWORD_RESET_COMPLETE, AuditOutcome.SUCCESS,
                tenantId, actorUserId, actorUsername,
                "Password of '" + user.getUsername() + "' reset by platform admin");
        log.warn("Platform admin {} reset password of {} in tenant {}",
                actorUsername, user.getUsername(), tenantId);

        return rawPassword;
    }

    /** ارتقای یک کاربر موجود به مدیر شرکت — وقتی مدیر قبلی رفته است. */
    @Transactional
    public UserResponse grantCompanyAdmin(Long tenantId, Long userId,
                                          Long actorUserId, String actorUsername) {
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new NotFoundException("user.not_found", userId));

        Role companyAdmin = roleRepository.findByNameAndTenantIdIsNull(DefaultRoles.COMPANY_ADMIN)
                .orElseThrow(() -> new IllegalStateException("COMPANY_ADMIN role missing"));

        if (user.getRoles().stream().anyMatch(r -> DefaultRoles.COMPANY_ADMIN.equals(r.getName()))) {
            throw new ConflictException("user.already_company_admin", user.getUsername());
        }

        user.getRoles().add(companyAdmin);
        user.setEnabled(true);
        user = userRepository.save(user);

        auditLogService.log(AuditEvent.USER_ROLE_CHANGED, AuditOutcome.SUCCESS,
                tenantId, actorUserId, actorUsername,
                "User '" + user.getUsername() + "' granted COMPANY_ADMIN by platform admin");
        log.warn("Platform admin {} granted COMPANY_ADMIN to {} in tenant {}",
                actorUsername, user.getUsername(), tenantId);

        return toResponse(user);
    }

    // ============================== HELPERS ==============================

    private Set<Role> resolveAdminAssignableRoles(List<String> roleNames) {
        Set<Role> result = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByNameAndTenantIdIsNull(name)
                    .orElseThrow(() -> new NotFoundException("role.not_found", name));
            if (ADMIN_BLOCKED_ROLES.contains(role.getName())) {
                throw new BusinessException("user.role.not_assignable", role.getName());
            }
            result.add(role);
        }
        return result;
    }

    /**
     * نقش‌های مجاز برای تخصیص توسط مدیر شرکت:
     *  - نقش‌های سیستمی فقط از سفیدلیست (SUPER_ADMIN و COMPANY_ADMIN مجاز نیستند)
     *  - نقش‌های سفارشی مجازند مگر دسترسی ارتقادهنده داشته باشند
     */
    private Set<Role> resolveAssignableRoles(List<String> roleNames) {
        Set<Role> result = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByNameAndTenantIdIsNull(name)
                    .orElseThrow(() -> new NotFoundException("role.not_found", name));

            if (role.isSystemRole() && !ASSIGNABLE_SYSTEM_ROLES.contains(role.getName())) {
                throw new BusinessException("user.role.not_assignable", role.getName());
            }
            if (!role.isSystemRole()) {
                boolean dangerous = role.getPermissions().stream().anyMatch(ESCALATION_PERMISSIONS::contains);
                if (dangerous) throw new BusinessException("user.role.not_assignable", role.getName());
            }
            result.add(role);
        }
        return result;
    }

    private String generatePassword() {
        // ۸ کاراکتر، بدون حروف مبهم (l/1/O/0). حداقل یک حرف و یک رقم تضمین می‌شود.
        StringBuilder sb = new StringBuilder(8);
        sb.append("abcdefghijkmnopqrstuvwxyz".charAt(RANDOM.nextInt(25)));
        sb.append("23456789".charAt(RANDOM.nextInt(8)));
        for (int i = 2; i < 8; i++) {
            sb.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        List<Character> chars = new ArrayList<>();
        for (char c : sb.toString().toCharArray()) chars.add(c);
        Collections.shuffle(chars, RANDOM);
        StringBuilder out = new StringBuilder(8);
        chars.forEach(out::append);
        return out.toString();
    }

    private User load(Long id) {
        Long tenantId = requireTenantId();
        return userRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NotFoundException("user.not_found", id));
    }

    private void guardNotSelf(User user, Long actorUserId, String messageKey) {
        if (user.getId().equals(actorUserId)) throw new BusinessException(messageKey);
    }

    private Long requireTenantId() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("user.tenant_required");
        return tenantId;
    }

    private UserResponse toResponse(User u) {
        boolean locked = !u.isAccountNonLocked()
                && (u.getLockedUntil() == null || u.getLockedUntil().isAfter(Instant.now()));
        return new UserResponse(
                u.getId(), u.getUsername(), u.getEmail(),
                u.getFirstName(), u.getLastName(),
                u.isEnabled(), locked, u.getLastLoginAt(),
                u.getRoles().stream().map(Role::getName).collect(Collectors.toSet())
        );
    }
}