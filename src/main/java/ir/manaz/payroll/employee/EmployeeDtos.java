package ir.manaz.payroll.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.manaz.common.validation.ValidIranianNationalId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTOهای کارمند — همه record و immutable.
 * توجه: personnel_code توسط سرور تولید می‌شود و در ورودی وجود ندارد.
 * national_id پس از ایجاد قابل تغییر نیست (اگر اشتباه بود، کارمند را حذف و مجدداً ایجاد کنید).
 */
public final class EmployeeDtos {

    private EmployeeDtos() {}

    @Schema(description = "درخواست ایجاد کارمند جدید")
    public record CreateEmployeeRequest(
            @Schema(description = "نام", example = "علی", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100)
            String firstName,

            @Schema(description = "نام خانوادگی", example = "محمدی", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100)
            String lastName,

            @Schema(description = "کد ملی ۱۰ رقمی", example = "0012345678", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            @ValidIranianNationalId
            String nationalId,

            @Schema(description = "تاریخ تولد", example = "1370-05-12", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            LocalDate birthDate,

            @Schema(description = "تاریخ استخدام", example = "1402-01-15", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            LocalDate hireDate,

            @Schema(description = "شماره تلفن (اختیاری)", example = "09121234567")
            @Size(max = 20)
            String phoneNumber,

            @Schema(description = "ایمیل (اختیاری)", example = "ali@example.com")
            @Email(message = "{employee.email.invalid}")
            @Size(max = 255)
            String email,

            @Schema(description = "تعداد فرزندان", example = "2")
            @Min(value = 0, message = "{employee.children_count.negative}")
            Integer childrenCount,

            @Schema(description = "شماره شبا (IR + ۲۴ رقم)", example = "IR820540102680020817909002")
            @Pattern(regexp = "^IR\\d{24}$", message = "{employee.iban.invalid}")
            String iban
    ) {}

    @Schema(description = "درخواست ویرایش کارمند — کد ملی و کد پرسنلی تغییرناپذیر هستند")
    public record UpdateEmployeeRequest(
            @Schema(description = "نام", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100)
            String firstName,

            @Schema(description = "نام خانوادگی", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100)
            String lastName,

            @Schema(description = "تاریخ تولد", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            LocalDate birthDate,

            @Schema(description = "تاریخ استخدام", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            LocalDate hireDate,

            @Schema(description = "شماره تلفن")
            @Size(max = 20)
            String phoneNumber,

            @Schema(description = "ایمیل")
            @Email(message = "{employee.email.invalid}")
            @Size(max = 255)
            String email,

            @Schema(description = "تعداد فرزندان")
            @Min(value = 0, message = "{employee.children_count.negative}")
            Integer childrenCount,

            @Schema(description = "شماره شبا")
            @Pattern(regexp = "^IR\\d{24}$", message = "{employee.iban.invalid}")
            String iban
    ) {}

    @Schema(description = "اطلاعات کامل یک کارمند")
    public record EmployeeResponse(
            @Schema(description = "شناسه داخلی", example = "42") Long id,
            @Schema(description = "کد پرسنلی (تولید خودکار)", example = "EMP-1-0042") String personnelCode,
            @Schema(description = "نام") String firstName,
            @Schema(description = "نام خانوادگی") String lastName,
            @Schema(description = "کد ملی") String nationalId,
            @Schema(description = "تاریخ تولد") LocalDate birthDate,
            @Schema(description = "تاریخ استخدام") LocalDate hireDate,
            @Schema(description = "شماره تلفن") String phoneNumber,
            @Schema(description = "ایمیل") String email,
            @Schema(description = "تعداد فرزندان") Integer childrenCount,
            @Schema(description = "شماره شبا") String iban,
            @Schema(description = "وضعیت فعال بودن") boolean active,
            @Schema(description = "زمان ایجاد") Instant createdAt,
            @Schema(description = "زمان آخرین ویرایش") Instant updatedAt
    ) {
        public static EmployeeResponse from(Employee e) {
            return new EmployeeResponse(
                    e.getId(), e.getPersonnelCode(), e.getFirstName(), e.getLastName(),
                    e.getNationalId(), e.getBirthDate(), e.getHireDate(),
                    e.getPhoneNumber(), e.getEmail(), e.getChildrenCount(), e.getIban(),
                    e.isActive(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }
}