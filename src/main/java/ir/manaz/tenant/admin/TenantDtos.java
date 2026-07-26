package ir.manaz.tenant.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.manaz.security.auth.validation.ValidPassword;
import jakarta.validation.constraints.*;

import java.time.Instant;

public final class TenantDtos {

    private TenantDtos() {}

    @Schema(description = "ثبت شرکت جدید همراه با اولین کاربر مدیر (COMPANY_ADMIN)")
    public record CreateTenantRequest(
            @Schema(description = "نام رسمی شرکت", example = "شرکت نمونه",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 200) String name,

            @Schema(description = "کد یکتای شرکت — پس از ثبت قابل تغییر نیست", example = "acme",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 50) String code,

            @Schema(description = "شناسه ملی شرکت", example = "10101234567")
            @Size(max = 20) String nationalId,

            @Schema(description = "کد کارگاه تأمین اجتماعی — برای ارسال لیست بیمه", example = "1234567890")
            @Size(max = 20) String insuranceWorkshopCode,

            @Schema(description = "کد اقتصادی / شناسه مالیاتی", example = "411111111111")
            @Size(max = 20) String economicCode,

            @Schema(description = "شبای شرکت — مبدأ پرداخت در فایل بانکی", example = "IR820540102680020817909002")
            @Pattern(regexp = "^IR\\d{24}$", message = "{tenant.iban.invalid}") String iban,

            @Schema(description = "آدرس شرکت") @Size(max = 500) String address,
            @Schema(description = "تلفن شرکت", example = "02112345678") @Size(max = 20) String phone,

            @Schema(description = "نام کاربری مدیر شرکت — سراسری یکتا", example = "acme.admin",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(min = 3, max = 100) String adminUsername,

            @Schema(description = "ایمیل مدیر شرکت — سراسری یکتا", example = "admin@acme.com",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Email @Size(max = 150) String adminEmail,

            @Schema(description = "رمز اولیه مدیر — حداقل ۸ کاراکتر شامل حرف و عدد",
                    format = "password", requiredMode = Schema.RequiredMode.REQUIRED)
            @ValidPassword String adminPassword,

            @Schema(example = "علی") @Size(max = 100) String adminFirstName,
            @Schema(example = "رضایی") @Size(max = 100) String adminLastName
    ) {}

    @Schema(description = "ویرایش شرکت — کد شرکت غیرقابل تغییر است")
    public record UpdateTenantRequest(
            @Schema(description = "نام رسمی شرکت") @Size(max = 200) String name,
            @Schema(description = "شناسه ملی شرکت") @Size(max = 20) String nationalId,
            @Schema(description = "کد کارگاه تأمین اجتماعی") @Size(max = 20) String insuranceWorkshopCode,
            @Schema(description = "کد اقتصادی") @Size(max = 20) String economicCode,
            @Schema(description = "شبای شرکت")
            @Pattern(regexp = "^IR\\d{24}$", message = "{tenant.iban.invalid}") String iban,
            @Schema(description = "آدرس") @Size(max = 500) String address,
            @Schema(description = "تلفن") @Size(max = 20) String phone
    ) {}

    @Schema(description = "اطلاعات شرکت")
    public record TenantResponse(
            Long id,
            String name,
            String code,
            String nationalId,
            String insuranceWorkshopCode,
            String economicCode,
            String iban,
            String address,
            String phone,
            boolean active,
            Instant createdAt,
            @Schema(description = "شناسه کاربر مدیر — فقط در پاسخ ثبت اولیه پر می‌شود") Long adminUserId,
            @Schema(description = "نام کاربری مدیر — فقط در پاسخ ثبت اولیه پر می‌شود") String adminUsername
    ) {}
}