package ir.manaz.email;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * پیاده‌سازی SMTP. با {@code app.email.enabled=true} فعال می‌شود.
 * ارسال {@code @Async} است — endpoint HTTP منتظر تحویل SMTP نمی‌ماند.
 * شکست ارسال تنها لاگ می‌شود چون:
 *  - forgot-password باید همیشه ۲۰۴ برگرداند تا email enumeration نشود
 *  - initial credentials نسخه فرانت هم موجود است
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final EmailProperties props;

    @Override
    @Async
    public void sendPasswordReset(String to, String rawToken, int validMinutes) {
        String encoded = URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String link = props.getPanelBaseUrl() + "/reset-password?token=" + encoded;
        String subject = "بازیابی رمز عبور";
        String body = """
                کاربر گرامی،

                برای تعیین رمز جدید روی لینک زیر کلیک کنید (اعتبار: %d دقیقه):

                %s

                اگر شما این درخواست را نداده‌اید، این ایمیل را نادیده بگیرید — رمز فعلی شما تغییری نمی‌کند.
                """.formatted(validMinutes, link);
        send(to, subject, body);
    }

    @Override
    @Async
    public void sendInitialCredentials(String to, String username, String rawPassword) {
        String subject = "حساب کاربری شما ساخته شد";
        String body = """
                حساب کاربری شما در سامانه دستمزد ساخته شد.

                نام کاربری: %s
                رمز اولیه: %s

                لطفاً پس از اولین ورود، رمز خود را از پنل تغییر دهید.
                آدرس پنل: %s
                """.formatted(username, rawPassword, props.getPanelBaseUrl());
        send(to, subject, body);
    }

    @Override
    @Async
    public void sendPasswordResetByAdmin(String to, String username, String rawPassword) {
        String subject = "رمز عبور شما توسط ادمین بازنشانی شد";
        String body = """
                رمز عبور حساب «%s» توسط مدیر سامانه بازنشانی شد.

                رمز جدید: %s

                لطفاً پس از ورود، رمز خود را از پنل تغییر دهید.
                آدرس پنل: %s
                """.formatted(username, rawPassword, props.getPanelBaseUrl());
        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(new InternetAddress(props.getFrom(), props.getFromName(), "UTF-8"));
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(message);
            log.info("Sent email subject='{}' to={}", subject, to);
        } catch (UnsupportedEncodingException | jakarta.mail.MessagingException | org.springframework.mail.MailException ex) {
            log.error("Failed to send email to={} subject={}: {}", to, subject, ex.getMessage());
        }
    }
}
