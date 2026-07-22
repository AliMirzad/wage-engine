package ir.manaz.audit;

public final class AuditEvent {

    private AuditEvent() {}

    // Auth
    public static final String LOGIN = "LOGIN";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String REGISTER = "REGISTER";
    public static final String ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

    // Password
    public static final String PASSWORD_CHANGE = "PASSWORD_CHANGE";
    public static final String PASSWORD_RESET_REQUEST = "PASSWORD_RESET_REQUEST";
    public static final String PASSWORD_RESET_COMPLETE = "PASSWORD_RESET_COMPLETE";

    // User admin
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";

    // Access
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

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
    public static final String EMPLOYEE_DELETED     = "EMPLOYEE_DELETED";
}
