package ir.manaz.security.permission.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

public final class AdminRolePermissionDtos {
    private AdminRolePermissionDtos() {}

    @Schema(description = "متادیتای یک دسترسی برای نمایش در UI")
    public record PermissionView(
            @Schema(description = "کد دسترسی (ثابت، غیرقابل تغییر)", example = "EMPLOYEE_WRITE")
            String code,

            @Schema(description = "توضیح فارسی قابل ویرایش", example = "ایجاد و ویرایش کارمندان")
            String descriptionFa,

            @Schema(description = "دسته‌بندی برای گروه‌بندی در UI", example = "EMPLOYEE")
            String category
    ) {}

    @Schema(description = "اطلاعات یک نقش با دسترسی‌های تخصیص‌یافته")
    public record RoleView(
            @Schema(description = "شناسه نقش", example = "1")
            Long id,

            @Schema(description = "نام نقش", example = "COMPANY_ADMIN")
            String name,

            @Schema(description = "توضیح نقش")
            String description,

            @Schema(description = "آیا نقش سیستمی است؟ (نقش‌های سیستمی غیرقابل ویرایش/حذف)", example = "true")
            boolean systemRole,

            @Schema(description = "شناسه شرکت — null برای نقش‌های سیستمی", nullable = true)
            Long tenantId,

            @Schema(description = "مجموعه کدهای دسترسی این نقش")
            Set<String> permissionCodes
    ) {}

    @Schema(description = "درخواست ساخت یک نقش جدید (system-wide)")
    public record CreateRoleRequest(
            @Schema(
                    description = "نام یکتای نقش — فقط حروف بزرگ انگلیسی، اعداد و _ ",
                    example = "WORKSHOP_SUPERVISOR",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @NotBlank
            @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                    message = "نام نقش باید فقط شامل حروف بزرگ انگلیسی، اعداد و _ باشد و با حرف شروع شود")
            String name,

            @Schema(description = "توضیح نقش (اختیاری)", example = "سرپرست کارگاه تولید")
            @Size(max = 255)
            String description,

            @Schema(
                    description = "لیست کدهای دسترسی تخصیص‌یافته به این نقش (می‌تواند خالی باشد)",
                    example = "[\"EMPLOYEE_READ\", \"CONTRACT_READ\"]"
            )
            List<String> permissionCodes
    ) {}

    @Schema(description = "درخواست ویرایش نام یا توضیح یک نقش (permissionها از این‌جا تغییر نمی‌کنند)")
    public record UpdateRoleRequest(
            @Schema(description = "نام جدید نقش — در صورت خالی، تغییر نمی‌کند", example = "SENIOR_SUPERVISOR")
            @Size(min = 3, max = 50)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]*$",
                    message = "نام نقش باید فقط شامل حروف بزرگ انگلیسی، اعداد و _ باشد و با حرف شروع شود")
            String name,

            @Schema(description = "توضیح جدید — در صورت خالی، تغییر نمی‌کند")
            @Size(max = 255)
            String description
    ) {}

    @Schema(description = "درخواست به‌روزرسانی کامل دسترسی‌های یک نقش")
    public record UpdateRolePermissionsRequest(
            @Schema(
                    description = "لیست کامل کدهای دسترسی (replace کامل، نه merge)",
                    example = "[\"EMPLOYEE_READ\", \"EMPLOYEE_WRITE\"]",
                    requiredMode = Schema.RequiredMode.REQUIRED
            )
            @NotNull
            @NotEmpty
            List<String> permissionCodes
    ) {}

    @Schema(description = "درخواست ویرایش متادیتای یک دسترسی")
    public record UpdatePermissionMetadataRequest(
            @Schema(description = "توضیح فارسی جدید — در صورت خالی بودن تغییر نمی‌کند",
                    example = "مشاهده لیست کارمندان شرکت")
            String descriptionFa,

            @Schema(description = "دسته‌بندی جدید — در صورت خالی بودن تغییر نمی‌کند",
                    example = "EMPLOYEE")
            String category
    ) {}
}
