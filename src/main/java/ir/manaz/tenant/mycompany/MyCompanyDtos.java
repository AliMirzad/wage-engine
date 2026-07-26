package ir.manaz.tenant.mycompany;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class MyCompanyDtos {

    private MyCompanyDtos() {}

    @Schema(description = "اطلاعات شرکت جاری")
    public record MyCompanyResponse(
            @Schema(description = "نام رسمی شرکت") String name,
            @Schema(description = "کد شرکت — غیرقابل تغییر") String code,
            @Schema(description = "شناسه ملی شرکت") String nationalId,
            @Schema(description = "کد کارگاه تأمین اجتماعی") String insuranceWorkshopCode,
            @Schema(description = "کد اقتصادی / شناسه مالیاتی") String economicCode,
            @Schema(description = "شبای شرکت — مبدأ پرداخت در فایل بانکی") String iban,
            @Schema(description = "آدرس شرکت") String address,
            @Schema(description = "تلفن شرکت") String phone
    ) {}

    @Schema(description = "ویرایش اطلاعات شرکت جاری — فقط فیلدهای ارسال‌شده به‌روز می‌شوند")
    public record UpdateMyCompanyRequest(
            @Schema(description = "نام رسمی شرکت", example = "شرکت نمونه")
            @Size(max = 200) String name,

            @Schema(description = "شناسه ملی شرکت", example = "10101234567")
            @Size(max = 20) String nationalId,

            @Schema(description = "کد کارگاه تأمین اجتماعی — برای ارسال لیست بیمه", example = "1234567890")
            @Size(max = 20) String insuranceWorkshopCode,

            @Schema(description = "کد اقتصادی / شناسه مالیاتی", example = "411111111111")
            @Size(max = 20) String economicCode,

            @Schema(description = "شبای شرکت", example = "IR820540102680020817909002")
            @Pattern(regexp = "^IR\\d{24}$", message = "{tenant.iban.invalid}") String iban,

            @Schema(description = "آدرس شرکت") @Size(max = 500) String address,
            @Schema(description = "تلفن شرکت", example = "02112345678") @Size(max = 20) String phone
    ) {}
}