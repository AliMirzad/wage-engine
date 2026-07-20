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
 * These roles have tenant_id = null and are visible to every tenant.
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
        seed(SUPER_ADMIN, "Full access across all tenants",
                EnumSet.allOf(Permission.class));

        seed(COMPANY_ADMIN, "Owner/admin of a single company",
                EnumSet.of(
                        USER_READ, USER_WRITE, USER_DELETE,
                        ROLE_READ, ROLE_WRITE,
                        PROJECT_READ, PROJECT_WRITE,
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
                        PROJECT_READ,
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

    private void seed(String name, String description, Set<Permission> perms) {
        roleRepository.findByNameAndTenantIdIsNull(name).ifPresentOrElse(
                existing -> {
                    // Keep permissions in sync with code (dev-friendly)
                    if (!existing.getPermissions().equals(perms)) {
                        existing.setPermissions(EnumSet.copyOf(perms));
                        roleRepository.save(existing);
                        log.info("Updated permissions for system role {}", name);
                    }
                },
                () -> {
                    Role role = Role.builder()
                            .name(name)
                            .description(description)
                            .systemRole(true)
                            .tenantId(null)
                            .permissions(EnumSet.copyOf(perms))
                            .build();
                    roleRepository.save(role);
                    log.info("Seeded system role {}", name);
                }
        );
    }
}
