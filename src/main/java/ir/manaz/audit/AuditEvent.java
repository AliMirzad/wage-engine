package ir.manaz.audit;

public final class AuditEvent {

    private AuditEvent() {}

    // Auth
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";

    // Password
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";
    public static final String PASSWORD_RESET_COMPLETE = "PASSWORD_RESET_COMPLETE";

    // Email verification
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";

    // User admin
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";

    // Roles / permissions
    public static final String ROLE_CREATED = "ROLE_CREATED";
    public static final String ROLE_UPDATED = "ROLE_UPDATED";
    public static final String ROLE_DELETED = "ROLE_DELETED";
    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";

    // Project
    public static final String PROJECT_CREATED   = "PROJECT_CREATED";
    public static final String PROJECT_UPDATED   = "PROJECT_UPDATED";
    public static final String PROJECT_ARCHIVED  = "PROJECT_ARCHIVED";
    public static final String PROJECT_RESTORED  = "PROJECT_RESTORED";

    //employee
    public static final String EMPLOYEE_CREATED     = "EMPLOYEE_CREATED";
    public static final String EMPLOYEE_UPDATED     = "EMPLOYEE_UPDATED";
    public static final String EMPLOYEE_DEACTIVATED = "EMPLOYEE_DEACTIVATED";
    public static final String EMPLOYEE_REACTIVATED = "EMPLOYEE_REACTIVATED";
    public static final String EMPLOYEE_TERMINATED  = "EMPLOYEE_TERMINATED";
    public static final String EMPLOYEE_DELETED     = "EMPLOYEE_DELETED";
    public static final String EMPLOYEE_REHIRED     = "EMPLOYEE_REHIRED";

    //contract
    public static final String CONTRACT_CREATED = "CONTRACT_CREATED";
    public static final String CONTRACT_UPDATED = "CONTRACT_UPDATED";
    public static final String CONTRACT_ENDED   = "CONTRACT_ENDED";
    public static final String CONTRACT_VOIDED  = "CONTRACT_VOIDED";

    //tenant
    // Tenant
    public static final String TENANT_CREATED     = "TENANT_CREATED";
    public static final String TENANT_UPDATED     = "TENANT_UPDATED";
    public static final String TENANT_DEACTIVATED = "TENANT_DEACTIVATED";
    public static final String TENANT_ACTIVATED   = "TENANT_ACTIVATED";
}
