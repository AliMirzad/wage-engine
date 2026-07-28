package ir.manaz.security.role;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

import static ir.manaz.security.role.Permission.*;

/**
 * Seeds the system-level roles into the database on first boot.
 *
 * <p>These roles have {@code tenant_id = null} and are visible to every tenant.
 *
 * <p><b>Important behavior change:</b> Once a role exists, this seeder does NOT
 * modify its permissions on subsequent startups. Role-permission assignments are
 * managed at runtime via the admin API. If a new {@link Permission} value needs to
 * be assigned to an existing role, do it either via the admin UI or with a
 * dedicated Flyway migration.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class DefaultRoles implements CommandLineRunner {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String MANAGER = "MANAGER";
    public static final String EMPLOYEE = "EMPLOYEE";
    public static final String AUDITOR = "AUDITOR";

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {

        syncSuperAdmin();

        seed(COMPANY_ADMIN, "Owner/admin of a single company",
                EnumSet.of(
                        USER_READ, USER_WRITE,
                        ROLE_READ,
                        PROJECT_READ, PROJECT_WRITE, PROJECT_FINANCIAL_READ,
                        EMPLOYEE_READ, EMPLOYEE_WRITE, EMPLOYEE_DELETE,
                        CONTRACT_READ, CONTRACT_WRITE,
                        PERFORMANCE_READ, PERFORMANCE_WRITE,
                        PAYROLL_CALCULATE, PAYROLL_READ, PAYROLL_APPROVE,
                        PAYSLIP_READ_ALL, PAYSLIP_PRINT,
                        BANK_FILE_EXPORT,
                        SETTINGS_READ, SETTINGS_WRITE,
                        REPORT_READ, REPORT_EXPORT,
                        AUDIT_LOG_READ
                ));

        seed(ACCOUNTANT, "Day-to-day accounting operator",
                EnumSet.of(
                        PROJECT_READ, PROJECT_WRITE,
                        EMPLOYEE_READ, EMPLOYEE_WRITE,
                        CONTRACT_READ, CONTRACT_WRITE,
                        PERFORMANCE_READ, PERFORMANCE_WRITE,
                        PAYROLL_CALCULATE, PAYROLL_READ,
                        PAYSLIP_READ_ALL, PAYSLIP_PRINT,
                        BANK_FILE_EXPORT,
                        SETTINGS_READ,
                        REPORT_READ
                ));

        seed(MANAGER, "CEO / management - reports only",
                EnumSet.of(
                        PROJECT_READ, PROJECT_FINANCIAL_READ,
                        EMPLOYEE_READ,
                        PAYROLL_READ, PAYROLL_APPROVE,
                        PAYSLIP_READ_ALL,
                        REPORT_READ, REPORT_EXPORT
                ));

        seed(EMPLOYEE, "Regular employee - can view own payslip",
                EnumSet.of(
                        PAYSLIP_READ_OWN
                ));

        seed(AUDITOR, "Read-only access for audit",
                EnumSet.of(
                        PROJECT_READ,
                        EMPLOYEE_READ,
                        CONTRACT_READ,
                        PERFORMANCE_READ,
                        PAYROLL_READ,
                        PAYSLIP_READ_ALL,
                        SETTINGS_READ,
                        REPORT_READ,
                        AUDIT_LOG_READ
                ));
    }

    /**
     * Seed-once: if the role already exists, its permissions are left untouched
     * (DB is source of truth; admin may have customized them at runtime).
     */
    private void seed(String name, String description, Set<Permission> perms) {
        roleRepository.findByNameAndTenantIdIsNull(name).ifPresentOrElse(
                existing -> log.debug("System role {} already exists; skipping seed", name),
                () -> {
                    Role role = Role.builder()
                            .name(name)
                            .description(description)
                            .systemRole(true)
                            .tenantId(null)
                            .permissions(EnumSet.copyOf(perms))
                            .build();
                    roleRepository.save(role);
                    log.info("Seeded system role {} with {} permissions", name, perms.size());
                }
        );
    }

    private void syncSuperAdmin() {
        var all = EnumSet.allOf(Permission.class);
        roleRepository.findByNameAndTenantIdIsNull(SUPER_ADMIN).ifPresentOrElse(
                existing -> {
                    if (!existing.getPermissions().containsAll(all)) {
                        existing.setPermissions(EnumSet.copyOf(all));
                        roleRepository.save(existing);
                        log.info("SUPER_ADMIN synced to all {} permissions", all.size());
                    }
                },
                () -> {
                    roleRepository.save(Role.builder()
                            .name(SUPER_ADMIN)
                            .description("Full access across all tenants")
                            .systemRole(true)
                            .tenantId(null)
                            .permissions(EnumSet.copyOf(all))
                            .build());
                    log.info("Seeded system role SUPER_ADMIN with {} permissions", all.size());
                }
        );
    }
}
