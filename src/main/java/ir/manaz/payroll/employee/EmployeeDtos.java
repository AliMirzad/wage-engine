package ir.manaz.payroll.employee;

import io.swagger.v3.oas.annotations.media.Schema;
import ir.manaz.common.validation.ValidIranianNationalId;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTOهای کارمند — همه record و immutable.
 * personnel_code توسط سرور تولید می‌شود و در ورودی وجود ندارد.
 * national_id پس از ایجاد قابل تغییر نیست.
 * ویرایش جزئی است: فیلدهای null دست‌نخورده می‌مانند.
 */
public final class EmployeeDtos {

    private EmployeeDtos() {}

    @Schema(description = "درخواست ایجاد کارمند جدید")
    public record CreateEmployeeRequest(
            @Schema(description = "نام", example = "علی", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String firstName,

            @Schema(description = "نام خانوادگی", example = "محمدی", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 100) String lastName,

            @Schema(description = "کد ملی ۱۰ رقمی", example = "0012345678",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @ValidIranianNationalId String nationalId,

            @Schema(description = "تاریخ تولد", example = "1991-08-03",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate birthDate,

            @Schema(description = "تاریخ استخدام", example = "2023-04-04",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate hireDate,

            @Schema(description = "شماره تلفن", example = "09121234567")
            @Size(max = 20) String phoneNumber,

            @Schema(description = "ایمیل", example = "ali@example.com")
            @Email(message = "{employee.email.invalid}") @Size(max = 255) String email,

            @Schema(description = "تعداد فرزندان — مبنای حق اولاد", example = "2")
            @Min(value = 0, message = "{employee.children_count.negative}") Integer childrenCount,

            @Schema(description = "شماره شبا (IR + ۲۴ رقم)", example = "IR820540102680020817909002")
            @Pattern(regexp = "^IR\\d{24}$", message = "{employee.iban.invalid}") String iban,

            @Schema(description = "نام بانک", example = "ملت") @Size(max = 100) String bankName,

            // ─── مؤثر در محاسبه ───
            @Schema(description = "وضعیت تأهل — بر معافیت مالیاتی اثر دارد. پیش‌فرض SINGLE",
                    example = "MARRIED")
            MaritalStatus maritalStatus,

            @Schema(description = "شماره بیمه تأمین اجتماعی", example = "1234567890")
            @Size(max = 20) String insuranceNumber,

            @Schema(description = "نوع بیمه. پیش‌فرض MANDATORY", example = "MANDATORY")
            InsuranceType insuranceType,

            @Schema(description = "نوع همکاری — مبنای محاسبه. پیش‌فرض FULL_TIME", example = "FULL_TIME")
            EmploymentType employmentType,

            @Schema(description = "سمت شغلی", example = "جوشکار") @Size(max = 100) String jobTitle,

            // ─── پرونده پرسنلی ───
            @Schema(description = "نام پدر", example = "حسین") @Size(max = 100) String fatherName,
            @Schema(description = "شماره شناسنامه", example = "1234") @Size(max = 20) String idCardNumber,
            @Schema(description = "محل صدور", example = "تهران") @Size(max = 100) String issuePlace,
            @Schema(description = "وضعیت نظام وظیفه", example = "COMPLETED") MilitaryStatus militaryStatus,
            @Schema(description = "سطح تحصیلات", example = "DIPLOMA") EducationLevel educationLevel,
            @Schema(description = "آدرس") @Size(max = 500) String address,
            @Schema(description = "نام مخاطب اضطراری") @Size(max = 100) String emergencyContactName,
            @Schema(description = "تلفن مخاطب اضطراری") @Size(max = 20) String emergencyContactPhone
    ) {}

    @Schema(description = "ویرایش کارمند — کد ملی و کد پرسنلی تغییرناپذیرند. فیلدهای ارسال‌نشده دست‌نخورده می‌مانند")
    public record UpdateEmployeeRequest(
            @Schema(description = "نام") @Size(max = 100) String firstName,
            @Schema(description = "نام خانوادگی") @Size(max = 100) String lastName,
            @Schema(description = "تاریخ تولد") LocalDate birthDate,
            @Schema(description = "تاریخ استخدام") LocalDate hireDate,
            @Schema(description = "شماره تلفن") @Size(max = 20) String phoneNumber,
            @Schema(description = "ایمیل") @Email(message = "{employee.email.invalid}") @Size(max = 255) String email,
            @Schema(description = "تعداد فرزندان")
            @Min(value = 0, message = "{employee.children_count.negative}") Integer childrenCount,
            @Schema(description = "شماره شبا")
            @Pattern(regexp = "^IR\\d{24}$", message = "{employee.iban.invalid}") String iban,
            @Schema(description = "نام بانک") @Size(max = 100) String bankName,

            MaritalStatus maritalStatus,
            @Schema(description = "شماره بیمه تأمین اجتماعی") @Size(max = 20) String insuranceNumber,
            InsuranceType insuranceType,
            EmploymentType employmentType,
            @Schema(description = "سمت شغلی") @Size(max = 100) String jobTitle,

            @Schema(description = "نام پدر") @Size(max = 100) String fatherName,
            @Schema(description = "شماره شناسنامه") @Size(max = 20) String idCardNumber,
            @Schema(description = "محل صدور") @Size(max = 100) String issuePlace,
            MilitaryStatus militaryStatus,
            EducationLevel educationLevel,
            @Schema(description = "آدرس") @Size(max = 500) String address,
            @Schema(description = "نام مخاطب اضطراری") @Size(max = 100) String emergencyContactName,
            @Schema(description = "تلفن مخاطب اضطراری") @Size(max = 20) String emergencyContactPhone
    ) {}

    @Schema(description = "ثبت ترک کار کارمند")
    public record TerminateEmployeeRequest(
            @Schema(description = "تاریخ ترک کار", example = "2026-07-20",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate terminationDate,

            @Schema(description = "دلیل ترک کار", example = "استعفا",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 500) String terminationReason
    ) {}

    @Schema(description = "اطلاعات کامل یک کارمند")
    public record EmployeeResponse(
            Long id,
            @Schema(description = "کد پرسنلی (تولید خودکار)", example = "EMP-1-0042") String personnelCode,
            String firstName,
            String lastName,
            String nationalId,
            LocalDate birthDate,
            LocalDate hireDate,
            String phoneNumber,
            String email,
            Integer childrenCount,
            String iban,
            String bankName,

            MaritalStatus maritalStatus,
            String insuranceNumber,
            InsuranceType insuranceType,
            EmploymentType employmentType,
            String jobTitle,

            String fatherName,
            String idCardNumber,
            String issuePlace,
            MilitaryStatus militaryStatus,
            EducationLevel educationLevel,
            String address,
            String emergencyContactName,
            String emergencyContactPhone,

            @Schema(description = "تاریخ ترک کار — خالی یعنی هنوز شاغل است") LocalDate terminationDate,
            String terminationReason,

            @Schema(description = "فعال بودن — توقف موقت، مستقل از ترک کار") boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static EmployeeResponse from(Employee e) {
            return new EmployeeResponse(
                    e.getId(), e.getPersonnelCode(), e.getFirstName(), e.getLastName(),
                    e.getNationalId(), e.getBirthDate(), e.getHireDate(),
                    e.getPhoneNumber(), e.getEmail(), e.getChildrenCount(),
                    e.getIban(), e.getBankName(),
                    e.getMaritalStatus(), e.getInsuranceNumber(),
                    e.getInsuranceType(), e.getEmploymentType(), e.getJobTitle(),
                    e.getFatherName(), e.getIdCardNumber(), e.getIssuePlace(),
                    e.getMilitaryStatus(), e.getEducationLevel(), e.getAddress(),
                    e.getEmergencyContactName(), e.getEmergencyContactPhone(),
                    e.getTerminationDate(), e.getTerminationReason(),
                    e.isActive(), e.getCreatedAt(), e.getUpdatedAt()
            );
        }
    }

    @Schema(description = "استخدام مجدد کارمندی که قبلاً ترک کار کرده است")
    public record RehireEmployeeRequest(
            @Schema(description = "تاریخ استخدام جدید", example = "2026-08-01",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate hireDate,

            @Schema(description = "توضیح — در تاریخچه ثبت می‌شود", example = "بازگشت پس از تسویه")
            @Size(max = 500) String note
    ) {}
}