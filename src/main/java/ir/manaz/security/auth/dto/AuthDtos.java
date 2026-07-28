package ir.manaz.security.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.manaz.security.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * All auth DTOs kept together as records for a compact base module.
 */
public final class AuthDtos {

    private AuthDtos() {}

    @Schema(description = "درخواست ورود")
    public record LoginRequest(
            @Schema(example = "admin", description = "username یا email")
            @NotBlank String usernameOrEmail,

            @Schema(example = "ChangeMe@123", format = "password")
            @NotBlank String password,

            @Schema(description = "اگر true باشد، refresh cookie با Max-Age بلند ذخیره می‌شود؛ وگرنه session cookie")
            Boolean remember
    ) {
        public boolean rememberMe() {
            return remember == null || remember;
        }
    }

    @Schema(description = "پاسخ ورود / refresh / register")
    public record LoginResponse(
            @Schema(description = "JWT کوتاه‌عمر — برای کلاینت‌های غیرمرورگر؛ SPA از cookie استفاده می‌کند") String accessToken,
            @Schema(description = "توکن بلندعمر — برای کلاینت‌های غیرمرورگر؛ SPA از cookie استفاده می‌کند") String refreshToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(example = "900", description = "طول عمر access token (ثانیه)") long expiresIn,
            UserInfo user
    ) {}

    @Schema(description = "پروفایل کاربر احراز شده")
    public record UserInfo(
            @Schema(example = "1") Long id,
            @Schema(example = "null", description = "برای SUPER_ADMIN مقدار null") Long tenantId,
            @Schema(example = "admin") String username,
            @Schema(example = "admin@example.com") String email,
            @Schema(description = "آیا ایمیل کاربر با OTP تأیید شده") boolean emailVerified,
            String firstName,
            String lastName,
            @Schema(example = "[\"COMPANY_ADMIN\"]") java.util.Set<String> roles,
            @Schema(example = "[\"EMPLOYEE_READ\",\"EMPLOYEE_WRITE\"]",
                    description = "لیست flat permissionها از همه roleها") java.util.Set<String> permissions
    ) {}

    @Schema(description = "درخواست تعویض access token — body اختیاری است اگر cookie موجود باشد")
    public record RefreshTokenRequest(
            @Schema(description = "refresh token از login قبلی؛ اگر خالی باشد از cookie خوانده می‌شود")
            String refreshToken
    ) {}

    @Schema(description = "درخواست کد بازیابی رمز")
    public record ForgotPasswordRequest(
            @Schema(example = "user@example.com") @NotBlank @Email String email
    ) {}

    @Schema(description = "تعیین رمز جدید با کد OTP دریافت‌شده در ایمیل")
    public record ResetPasswordRequest(
            @Schema(example = "user@example.com", description = "ایمیل کاربر — همان که کد به آن ارسال شد")
            @NotBlank @Email String email,
            @Schema(example = "123456", description = "کد ۶ رقمی OTP")
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^\\d{6}$",
                    message = "کد باید ۶ رقم عددی باشد") String code,
            @Schema(example = "NewPass1", format = "password") @ValidPassword String newPassword
    ) {}

    @Schema(description = "تأیید ایمیل حساب با کد OTP")
    public record VerifyEmailRequest(
            @Schema(example = "123456", description = "کد ۶ رقمی از ایمیل")
            @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^\\d{6}$",
                    message = "کد باید ۶ رقم عددی باشد") String code
    ) {}

    @Schema(description = "تغییر رمز توسط کاربر لاگین‌شده")
    public record ChangePasswordRequest(
            @Schema(format = "password") @NotBlank String currentPassword,
            @Schema(example = "NewPass1", format = "password") @ValidPassword String newPassword
    ) {}

    @Schema(description = "درخواست logout — refresh را از body یا cookie revoke می‌کند")
    public record LogoutRequest(
            @Schema(description = "اختیاری اگر cookie موجود باشد") String refreshToken
    ) {}
}
