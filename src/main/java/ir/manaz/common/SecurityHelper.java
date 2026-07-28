package ir.manaz.common;

import ir.manaz.security.jwt.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** دسترسی به کاربر جاری از SecurityContext — قبلاً در هر سرویس تکرار می‌شد. */
@Component
public class SecurityHelper {

    public AuthenticatedPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && auth.getPrincipal() instanceof AuthenticatedPrincipal p) ? p : null;
    }

    public Long currentUserId() {
        AuthenticatedPrincipal p = currentPrincipal();
        return p == null ? null : p.userId();
    }

    public String currentUsername() {
        AuthenticatedPrincipal p = currentPrincipal();
        return p == null ? null : p.username();
    }
}
