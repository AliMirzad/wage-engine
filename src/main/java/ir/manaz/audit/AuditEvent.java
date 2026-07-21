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
    public static final String ROLE_PERMISSIONS_UPDATED = "ROLE_PERMISSIONS_UPDATED";
}
