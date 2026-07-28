-- =========================================================================
--  V4 : جایگزینی password_reset_tokens با otp_codes و افزودن تأیید ایمیل
--
--  چرا:
--   * بازیابی رمز از «لینک ایمیل» به «کد ۶ رقمی» تغییر می‌کند تا مستقل
--     از reliability تحویل ایمیل باشد و UX برای کاربر ایرانی طبیعی‌تر شود.
--   * جدول otp_codes چند purpose را پشتیبانی می‌کند: PASSWORD_RESET،
--     EMAIL_VERIFICATION و در آینده 2FA — بدون تکرار طرح.
--   * فیلد attempts برای دفاع در برابر brute-force حدس کد اضافه شده که در
--     password_reset_tokens نبود.
--   * email_verified_at روی users اضافه می‌شود تا بعد از onboarding کاربر
--     ایمیلش را تأیید کند. عملیات حساس (reset-password) نیازمند تأیید ایمیل است.
-- =========================================================================

-- ---------- otp_codes ----------
CREATE TABLE otp_codes (
    id             BIGSERIAL   PRIMARY KEY,
    user_id        BIGINT      NOT NULL,
    -- SHA-256 hex از کد raw. کد raw فقط داخل ایمیل ارسال می‌شود.
    code_hash      VARCHAR(128) NOT NULL,
    -- PASSWORD_RESET | EMAIL_VERIFICATION (enum سمت اپ)
    purpose        VARCHAR(40) NOT NULL,
    -- تعداد تلاش‌های ناموفق تا اینجا. اگر به max_attempts برسد، کد invalidate می‌شود.
    attempts       INTEGER     NOT NULL DEFAULT 0,
    max_attempts   INTEGER     NOT NULL DEFAULT 5,
    expires_at     TIMESTAMP   NOT NULL,
    created_at     TIMESTAMP   NOT NULL,
    consumed_at    TIMESTAMP,
    CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- lookup سریع در verify: تنها یک کد فعال از هر purpose برای هر کاربر منطقی است
-- (کدهای قبلی هنگام تولید کد جدید invalidate می‌شوند).
CREATE INDEX idx_otp_user_purpose ON otp_codes(user_id, purpose);
-- کمک به cleanup روزانه‌ی expiredها.
CREATE INDEX idx_otp_expires ON otp_codes(expires_at);

-- ---------- users.email_verified_at ----------
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMP;

-- ---------- drop legacy password_reset_tokens ----------
-- توکن‌های موجود اعتبار خود را از دست می‌دهند. کاربرانی که در آستانه بازیابی رمز
-- بودند باید دوباره /auth/forgot-password بزنند تا کد OTP دریافت کنند.
DROP TABLE IF EXISTS password_reset_tokens;
