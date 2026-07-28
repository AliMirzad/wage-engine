package ir.manaz.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * fallback در محیط dev — به‌جای ارسال واقعی، محتوای ایمیل را در سطح debug
 * لاگ می‌کند. عمداً DEBUG است تا رمز اولیه/کد OTP در لاگ prod (که
 * معمولاً INFO+ است) لو نرود اگر اپراتور اشتباهاً enabled=false گذاشت.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    @Override
    public void sendPasswordResetOtp(String to, String code, int validMinutes) {
        log.debug("[EMAIL:password-reset-otp] to={} code={} validMinutes={}", to, code, validMinutes);
    }

    @Override
    public void sendEmailVerificationOtp(String to, String code, int validMinutes) {
        log.debug("[EMAIL:email-verification-otp] to={} code={} validMinutes={}", to, code, validMinutes);
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
