package ir.manaz.email;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * تنظیمات ارسال ایمیل. اگر {@code app.email.enabled=false} باشد،
 * پیاده‌سازی logging جایگزین می‌شود و هیچ SMTP‌ای صدا زده نمی‌شود.
 * <p>
 * تنظیمات SMTP خودِ اتصال (host/port/user/pass/tls) در
 * {@code spring.mail.*} از application.yml خوانده می‌شود — این کلاس
 * فقط سطح دامنه‌ی اپ (from/base-url/enabled) را نگه می‌دارد.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.email")
public class EmailProperties {
    /** غیرفعال به معنای «فقط لاگ کن، ارسال نکن» — مناسب dev. */
    private boolean enabled = false;
    private String from = "no-reply@example.com";
    private String fromName = "سامانه دستمزد";
    /** آدرس پایه پنل — در متن ایمیل برای ساخت لینک reset استفاده می‌شود. */
    private String panelBaseUrl = "http://localhost:3000";
}
