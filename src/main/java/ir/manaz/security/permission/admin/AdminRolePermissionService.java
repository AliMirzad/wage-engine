package ir.manaz.security.permission.admin;

import ir.manaz.audit.AuditEvent;
import ir.manaz.audit.AuditLogService;
import ir.manaz.audit.AuditOutcome;
import ir.manaz.exception.BusinessException;
import ir.manaz.exception.NotFoundException;
import ir.manaz.security.permission.PermissionDefinition;
import ir.manaz.security.permission.PermissionRepository;
import ir.manaz.security.role.DefaultRoles;
import ir.manaz.security.role.Permission;
import ir.manaz.security.role.Role;
import ir.manaz.security.role.RoleRepository;
import ir.manaz.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AuditLogService auditLogService;

    // -------- Read --------

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

    // -------- Write --------

    @Transactional
    public RoleView updatePermissions(Long roleId, List<String> newCodes,
                                      Long actorUserId, String actorUsername) {
        Role role = loadRole(roleId);

        // 1. Validate every code: exists in enum AND in catalog
        Set<Permission> newPerms = EnumSet.noneOf(Permission.class);
        for (String code : newCodes) {
            Permission p;
            try {
                p = Permission.valueOf(code);
            } catch (IllegalArgumentException ex) {
                throw new BusinessException("permission.code.invalid", code);
            }
            if (!permissionRepository.existsByCode(code)) {
                throw new NotFoundException("permission.not_found", code);
            }
            newPerms.add(p);
        }

        // 2. Safety: SUPER_ADMIN must keep ROLE_WRITE or nobody can manage roles anymore
        if (DefaultRoles.SUPER_ADMIN.equals(role.getName())
                && !newPerms.contains(Permission.ROLE_WRITE)) {
            throw new BusinessException("permission.super_admin.cannot_remove_role_write");
        }

        // 3. Apply
        String oldCodesStr = role.getPermissions().stream()
                .map(Enum::name).sorted().collect(Collectors.joining(","));
        role.setPermissions(newPerms);
        role = roleRepository.save(role);
        String newCodesStr = newPerms.stream()
                .map(Enum::name).sorted().collect(Collectors.joining(","));

        // 4. Audit
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

    // -------- Helpers --------

    private Role loadRole(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new NotFoundException("role.not_found", roleId));
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
}
