package ir.manaz.security.role;

/**
 * Fine-grained permissions used across the app.
 * Authorization checks are done against permissions (not roles):
 *   @PreAuthorize("hasAuthority('EMPLOYEE_WRITE')")
 *
 * Roles are just bundles of permissions.
 */
public enum Permission {

    // Tenant / company management (SUPER_ADMIN only)
    TENANT_READ,
    TENANT_WRITE,
    TENANT_DELETE,

    // User management inside a tenant
    USER_READ,
    USER_WRITE,
    USER_DELETE,

    // Role & permission management
    ROLE_READ,
    ROLE_WRITE,

    // Projects
    PROJECT_READ,
    PROJECT_WRITE,
    PROJECT_FINANCIAL_READ,

    // Employees
    EMPLOYEE_READ,
    EMPLOYEE_WRITE,
    EMPLOYEE_DELETE,

    // Contracts
    CONTRACT_READ,
    CONTRACT_WRITE,

    // Monthly performance / attendance
    PERFORMANCE_READ,
    PERFORMANCE_WRITE,

    // Payroll
    PAYROLL_CALCULATE,
    PAYROLL_READ,
    PAYROLL_APPROVE,

    // Payslips
    PAYSLIP_READ_OWN,
    PAYSLIP_READ_ALL,
    PAYSLIP_PRINT,

    // Bank file export
    BANK_FILE_EXPORT,

    // Payroll rules / settings
    SETTINGS_READ,
    SETTINGS_WRITE,

    // Reports
    REPORT_READ,
    REPORT_EXPORT,

    // Audit log
    AUDIT_LOG_READ;


    public String authority() {
        return name();
    }
}
