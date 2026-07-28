package ir.manaz.security.otp;

/**
 * نوع کاربرد یک OTP. هر purpose scope مستقل دارد — یعنی کد بازیابی رمز
 * برای تأیید ایمیل قابل استفاده نیست و بالعکس.
 */
public enum OtpPurpose {
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
