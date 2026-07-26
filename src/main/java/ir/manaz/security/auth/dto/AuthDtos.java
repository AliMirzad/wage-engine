package ir.manaz.security.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.manaz.security.auth.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
            @NotBlank String password
    ) {}

    @Schema(description = "پاسخ ورود / refresh / register")
    public record LoginResponse(
            @Schema(description = "JWT کوتاه‌عمر (۱۵ دقیقه)") String accessToken,
            @Schema(description = "توکن بلندعمر (۷ روز) — فقط با /auth/refresh") String refreshToken,
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
            String firstName,
            String lastName,
            @Schema(example = "[\"COMPANY_ADMIN\"]") java.util.Set<String> roles,
            @Schema(example = "[\"EMPLOYEE_READ\",\"EMPLOYEE_WRITE\"]",
                    description = "لیست flat permissionها از همه roleها") java.util.Set<String> permissions
    ) {}

    @Schema(description = "درخواست ثبت‌نام کاربر جدید")
    public record RegisterRequest(
            @Schema(example = "acme", description = "کد tenant موجود") @NotBlank @Size(max = 50) String tenantCode,
            @Schema(example = "john.doe") @NotBlank @Size(min = 3, max = 100) String username,
            @Schema(example = "john@acme.com") @NotBlank @Email @Size(max = 150) String email,
            @Schema(example = "StrongPass1", format = "password",
                    description = "حداقل ۸ کاراکتر، شامل حرف و عدد") @ValidPassword String password,
            @Schema(example = "John") @Size(max = 100) String firstName,
            @Schema(example = "Doe") @Size(max = 100) String lastName
    ) {}

    @Schema(description = "درخواست تعویض access token")
    public record RefreshTokenRequest(
            @Schema(description = "refresh token از login قبلی") @NotBlank String refreshToken
    ) {}

    @Schema(description = "درخواست بازیابی رمز")
    public record ForgotPasswordRequest(
            @Schema(example = "user@example.com") @NotBlank @Email String email
    ) {}

    @Schema(description = "تعیین رمز جدید با token")
    public record ResetPasswordRequest(
            @Schema(description = "token از /forgot-password") @NotBlank String token,
            @Schema(example = "NewPass1", format = "password") @ValidPassword String newPassword
    ) {}

    @Schema(description = "تغییر رمز توسط کاربر لاگین‌شده")
    public record ChangePasswordRequest(
            @Schema(format = "password") @NotBlank String currentPassword,
            @Schema(example = "NewPass1", format = "password") @ValidPassword String newPassword
    ) {}

    @Schema(description = "درخواست logout — refresh token را revoke می‌کند")
    public record LogoutRequest(
            @NotBlank String refreshToken
    ) {}
}
