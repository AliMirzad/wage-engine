package ir.manaz.security.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class UserDtos {

    private UserDtos() {}

    @Schema(description = "ساخت کاربر جدید در شرکت جاری — رمز اولیه توسط سرور تولید می‌شود")
    public record CreateUserRequest(
            @Schema(description = "نام کاربری — در کل سامانه یکتا", example = "acme.accountant",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 3, max = 100) String username,

            @Schema(description = "ایمیل — در کل سامانه یکتا", example = "hesabdar@acme.com",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 150) String email,

            @Schema(example = "زهرا") @Size(max = 100) String firstName,
            @Schema(example = "کریمی") @Size(max = 100) String lastName,

            @Schema(description = "نام نقش‌ها. نقش‌های سیستمی مجاز: ACCOUNTANT، MANAGER، EMPLOYEE، AUDITOR. نقش‌های سفارشی نیز مجازند مگر دسترسی خطرناک داشته باشند.",
                    example = "[\"ACCOUNTANT\"]", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotEmpty List<String> roleNames
    ) {}

    @Schema(description = "ویرایش کاربر — نام کاربری غیرقابل تغییر است")
    public record UpdateUserRequest(
            @Schema(description = "ایمیل — در کل سامانه یکتا") @Email @Size(max = 150) String email,
            @Schema(example = "زهرا") @Size(max = 100) String firstName,
            @Schema(example = "کریمی") @Size(max = 100) String lastName,
            @Schema(description = "جایگزینی کامل نقش‌ها. اگر null باشد نقش‌ها دست‌نخورده می‌مانند.")
            List<String> roleNames
    ) {}

    @Schema(description = "اطلاعات کاربر")
    public record UserResponse(
            Long id,
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            @Schema(description = "قفل موقت به دلیل تلاش ناموفق") boolean locked,
            Instant lastLoginAt,
            Set<String> roles
    ) {}

    @Schema(description = "پاسخ ساخت کاربر — رمز اولیه فقط همین یک بار نمایش داده می‌شود")
    public record CreateUserResponse(
            UserResponse user,
            @Schema(description = "رمز اولیه. در هیچ جای دیگری قابل بازیابی نیست؛ آن را به کاربر تحویل دهید.",
                    example = "k7Rm2Xq9") String initialPassword
    ) {}
}