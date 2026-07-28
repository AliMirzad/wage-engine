package ir.manaz.email;

/**
 * قرارداد ارسال ایمیل. دو پیاده‌سازی موجود است:
 * <ul>
 *   <li>{@link SmtpEmailService} برای prod — فعال با {@code app.email.enabled=true}</li>
 *   <li>{@link LoggingEmailService} برای dev — پیام‌ها فقط در لاگ ثبت می‌شوند</li>
 * </ul>
 * سرویس‌های تجاری نباید Async بودن یا شکست ارسال را دیده شوند —
 * پیاده‌سازی SMTP خودش @Async است و در صورت شکست فقط لاگ می‌کند.
 */
public interface EmailService {

    /** کد ۶ رقمی بازیابی رمز به‌همراه مدت اعتبار (دقیقه). */
    void sendPasswordResetOtp(String to, String code, int validMinutes);

    /** کد ۶ رقمی تأیید ایمیل که پس از ساخت حساب برای کاربر ارسال می‌شود. */
    void sendEmailVerificationOtp(String to, String code, int validMinutes);

    /**
     * رمز اولیه‌ای که سرور برای کاربر تازه ساخته‌شده تولید کرده — یک بار مصرف.
     * فرانت هم آن را نمایش می‌دهد، این ایمیل نسخه پشتیبان برای کاربر است.
     */
    void sendInitialCredentials(String to, String username, String rawPassword);

    /** اطلاع‌رسانی: رمز کاربر توسط ادمین ریست شد و رمز جدید داخل ایمیل است. */
    void sendPasswordResetByAdmin(String to, String username, String rawPassword);
}
