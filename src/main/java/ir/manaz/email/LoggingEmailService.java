package ir.manaz.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * fallback در محیط dev — به‌جای ارسال واقعی، محتوای ایمیل را در سطح debug
 * لاگ می‌کند. عمداً DEBUG است تا رمز اولیه/توکن reset در لاگ prod (که
 * معمولاً INFO+ است) لو نرود اگر اپراتور اشتباهاً enabled=false گذاشت.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void sendPasswordReset(String to, String rawToken, int validMinutes) {
        log.debug("[EMAIL:password-reset] to={} token={} validMinutes={}", to, rawToken, validMinutes);
    }

    @Override
    public void sendInitialCredentials(String to, String username, String rawPassword) {
        log.debug("[EMAIL:initial-credentials] to={} username={} password={}", to, username, rawPassword);
    }

    @Override
    public void sendPasswordResetByAdmin(String to, String username, String rawPassword) {
        log.debug("[EMAIL:admin-reset] to={} username={} password={}", to, username, rawPassword);
    }
}
