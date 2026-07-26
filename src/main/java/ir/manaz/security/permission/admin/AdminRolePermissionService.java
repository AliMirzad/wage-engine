package ir.manaz.security.permission.admin;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.ConflictException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.security.permission.PermissionDefinition;
import ir.manaz.security.permission.PermissionRepository;
import ir.manaz.security.role.DefaultRoles;
import ir.manaz.security.role.Permission;
import ir.manaz.security.role.Role;
import ir.manaz.security.role.RoleRepository;
import ir.manaz.security.user.UserRepository;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static ir.manaz.security.permission.admin.AdminRolePermissionDtos.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRolePermissionService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    // ============================== READ ==============================

    @Transactional(readOnly = true)
    public List<RoleView> listRoles() {
        // TODO: when tenant-scoped roles arrive, filter by TenantContext.getTenantId()
        //       unless caller is SUPER_ADMIN (tenantId == null in JWT)
        return roleRepository.findAll().stream()
                .map(this::toRoleView)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleView getRole(Long roleId) {
        return toRoleView(loadRole(roleId));
    }

    @Transactional(readOnly = true)
    public List<PermissionView> listPermissions(List<PermissionDefinition> all) {
        return all.stream()
                .map(p -> new PermissionView(p.getCode(), p.getDescriptionFa(), p.getCategory()))
                .toList();
    }

    // ============================== CREATE ==============================

    @Transactional
    public RoleView createRole(CreateRoleRequest req, Long actorUserId, String actorUsername) {
        // 1. Prevent using a reserved system role name (SUPER_ADMIN, COMPANY_ADMIN, ...)
        if (isSystemRoleName(req.name())) {
            throw new ConflictException("role.name.reserved", req.name());
        }

        if (collidesWithPermissionAuthority(req.name())) {
            throw new ConflictException("role.name.collides_permission", req.name());
        }

        // 2. Validate permission codes (may be empty list)
        Set<Permission> perms = validateAndConvert(req.permissionCodes());

        Role role = Role.builder()
                .name(req.name())
                .description(req.description())
                .systemRole(false)
                .tenantId(null)  // system-wide (see architecture doc)
                .permissions(perms)
                .build();

        Role saved;
        try {
            saved = roleRepository.save(role);
        } catch (DataIntegrityViolationException ex) {
            // uk_role_tenant_name (tenant_id, name) violation
            throw new ConflictException("role.name.duplicate", req.name());
        }

        String permsStr = perms.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
        auditLogService.log(
                AuditEvent.ROLE_CREATED,
                AuditOutcome.SUCCESS,
                TenantContext.getTenantId(),
                actorUserId,
                actorUsername,
                "Role '" + saved.getName() + "' created with permissions=[" + permsStr + "]"
        );
        log.info("Role {} created by {} with permissions=[{}]", saved.getName(), actorUsername, permsStr);

        return toRoleView(saved);
    }

    // ============================== UPDATE NAME / DESCRIPTION ==============================

    @Transactional
    public RoleView updateRole(Long roleId, UpdateRoleRequest req, Long actorUserId, String actorUsername) {
        Role role = loadRole(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("role.system.cannot_modify");
        }

        String oldName = role.getName();
        String oldDesc = role.getDescription();
        boolean changed = false;

        if (req.name() != null && !req.name().isBlank() && !req.name().equals(role.getName())) {
            if (isSystemRoleName(req.name())) {
                throw new ConflictException("role.name.reserved", req.name());
            }

            if (collidesWithPermissionAuthority(req.name())) {
                throw new ConflictException("role.name.collides_permission", req.name());
            }

            role.setName(req.name());
            changed = true;
        }
        if (req.description() != null && !req.description().equals(role.getDescription())) {
            role.setDescription(req.description());
            changed = true;
        }

        if (!changed) {
            return toRoleView(role);
        }

        Role saved;
        try {
            saved = roleRepository.save(role);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("role.name.duplicate", req.name());
        }

        auditLogService.log(
                AuditEvent.ROLE_UPDATED,
                AuditOutcome.SUCCESS,
                TenantContext.getTenantId(),
                actorUserId,
                actorUsername,
                "Role " + roleId + " updated. name: '" + oldName + "' -> '" + saved.getName()
                        + "', description: '" + oldDesc + "' -> '" + saved.getDescription() + "'"
        );
        log.info("Role {} updated by {}", saved.getName(), actorUsername);

        return toRoleView(saved);
    }

    // ============================== UPDATE PERMISSIONS ==============================

    @Transactional
    public RoleView updatePermissions(Long roleId, List<String> newCodes,
                                      Long actorUserId, String actorUsername) {
        Role role = loadRole(roleId);

        Set<Permission> newPerms = validateAndConvert(newCodes);

        // SUPER_ADMIN permissions are immutable — DefaultRoles.syncSuperAdmin()
        // forces allOf() on every boot, so any change here would silently revert.
        if (DefaultRoles.SUPER_ADMIN.equals(role.getName()) && role.getTenantId() == null) {
            throw new BusinessException("role.super_admin.immutable_permissions");
        }

        String oldCodesStr = role.getPermissions().stream()
                .map(Enum::name).sorted().collect(Collectors.joining(","));
        role.setPermissions(newPerms);
        role = roleRepository.save(role);
        String newCodesStr = newPerms.stream()
                .map(Enum::name).sorted().collect(Collectors.joining(","));

        auditLogService.log(
                AuditEvent.ROLE_PERMISSIONS_UPDATED,
                AuditOutcome.SUCCESS,
                TenantContext.getTenantId(),
                actorUserId,
                actorUsername,
                "Role '" + role.getName() + "' permissions changed. "
                        + "Before=[" + oldCodesStr + "] After=[" + newCodesStr + "]"
        );
        log.info("Role {} permissions updated by user {}: [{}] -> [{}]",
                role.getName(), actorUsername, oldCodesStr, newCodesStr);

        return toRoleView(role);
    }

    // ============================== DELETE ==============================

    @Transactional
    public void deleteRole(Long roleId, Long actorUserId, String actorUsername) {
        Role role = loadRole(roleId);

        if (role.isSystemRole()) {
            throw new BusinessException("role.system.cannot_delete");
        }

        // Block if any user still has this role
        long count = userRepository.countByRoles_Id(roleId);
        if (count > 0) {
            throw new ConflictException("role.in_use", count);
        }

        String name = role.getName();
        roleRepository.delete(role);

        auditLogService.log(
                AuditEvent.ROLE_DELETED,
                AuditOutcome.SUCCESS,
                TenantContext.getTenantId(),
                actorUserId,
                actorUsername,
                "Role '" + name + "' (id=" + roleId + ") deleted"
        );
        log.info("Role {} deleted by {}", name, actorUsername);
    }

    // ============================== HELPERS ==============================

    private Set<Permission> validateAndConvert(List<String> codes) {
        Set<Permission> result = EnumSet.noneOf(Permission.class);
        if (codes == null || codes.isEmpty()) {
            return result;
        }
        for (String code : codes) {
            Permission p;
            try {
                p = Permission.valueOf(code);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("permission.code.invalid", code);
            }
            if (!permissionRepository.existsByCode(code)) {
                throw new NotFoundException("permission.not_found", code);
            }
            result.add(p);
        }
        return result;
    }

    private Role loadRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found", roleId));
    }

    private boolean isSystemRoleName(String name) {
        return List.of(
                DefaultRoles.SUPER_ADMIN,
                DefaultRoles.COMPANY_ADMIN,
                DefaultRoles.ACCOUNTANT,
                DefaultRoles.MANAGER,
                DefaultRoles.EMPLOYEE,
                DefaultRoles.AUDITOR
        ).contains(name);
    }

    private RoleView toRoleView(Role r) {
        return new RoleView(
                r.getId(),
                r.getName(),
                r.getDescription(),
                r.isSystemRole(),
                r.getTenantId(),
                r.getPermissions().stream().map(Enum::name).collect(Collectors.toSet())
        );
    }

    /**
     * buildAuthorities() نقش را به "ROLE_" + name تبدیل می‌کند. اگر نامی مثل WRITE
     * انتخاب شود، authority حاصل ("ROLE_WRITE") با کد permission همنام تصادم
     * می‌کند و به ارتقای ناخواسته دسترسی منجر می‌شود.
     */
    private boolean collidesWithPermissionAuthority(String name) {
        if (name == null) return false;
        String authority = "ROLE_" + name.trim().toUpperCase();
        return EnumSet.allOf(Permission.class).stream()
                .anyMatch(p -> p.authority().equals(authority));
    }
}
