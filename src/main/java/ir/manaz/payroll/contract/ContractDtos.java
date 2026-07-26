package ir.manaz.payroll.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTOهای قرارداد. تمام فیلدهای مالی BigDecimal هستند.
 * شماره قرارداد سرور تولید می‌کند (CT-{tenantId}-{4-digit-seq}).
 * تمام فیلدها به‌جز notes، terms و jobTitle پس از ایجاد تغییرناپذیر هستند —
 * برای تغییر حقوق یا تاریخ، این قرارداد را end کنید و قرارداد جدید بسازید
 * (با previousContractId اشاره به این). این یک الزام حقوقی است
 * (قانون کار + گزارش‌دهی بیمه و مالیات).
 * <p>
 * تمام تاریخ‌ها میلادی (ISO-8601) هستند؛ تبدیل به شمسی وظیفه لایه نمایش است.
 */
public final class ContractDtos {

    private ContractDtos() {}

    @Schema(description = "درخواست ایجاد قرارداد جدید")
    public record CreateContractRequest(
            @Schema(description = "شناسه کارمند", example = "42",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long employeeId,

            @Schema(description = "شناسه پروژه — پروژه باید در وضعیت ACTIVE باشد", example = "7",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long projectId,

            @Schema(description = """
                    نوع حقوقی قرارداد. TEMPORARY و PROBATIONARY الزاماً تاریخ پایان
                    می‌خواهند؛ PERMANENT نباید تاریخ پایان داشته باشد. پیش‌فرض TEMPORARY.
                    """, example = "TEMPORARY")
            ContractType contractType,

            @Schema(description = """
                    مبنای محاسبه حقوق پایه. MONTHLY: مبلغ ثابت ماهانه،
                    DAILY: در روزهای کارکرد ضرب می‌شود، HOURLY: در ساعات کارکرد.
                    پیش‌فرض MONTHLY.
                    """, example = "MONTHLY")
            SalaryBasis salaryBasis,

            @Schema(description = "حقوق پایه — بر اساس salaryBasis تفسیر می‌شود", example = "50000000",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @DecimalMin(value = "0.0001", message = "{contract.base_salary.non_positive}")
            BigDecimal baseSalary,

            @Schema(description = "حق مسکن", example = "9000000")
            @DecimalMin(value = "0") BigDecimal housingAllowance,

            @Schema(description = "حق خواروبار (بن)", example = "14000000")
            @DecimalMin(value = "0") BigDecimal foodAllowance,

            @Schema(description = "حق اولاد پایه — به ازای هر فرزند", example = "5000000")
            @DecimalMin(value = "0") BigDecimal childAllowanceBase,

            @Schema(description = "حق ایاب و ذهاب", example = "3000000")
            @DecimalMin(value = "0") BigDecimal transportAllowance,

            @Schema(description = "پایه سنوات — قانوناً برای سابقه بیش از یک سال الزامی است",
                    example = "2000000")
            @DecimalMin(value = "0") BigDecimal seniorityPay,

            @Schema(description = "حق بدی آب و هوا / سختی کار", example = "4000000")
            @DecimalMin(value = "0") BigDecimal hardshipAllowance,

            @Schema(description = "ساعات کار هفتگی — مبنای محاسبه اضافه‌کاری. سقف قانونی ۴۴ ساعت. پیش‌فرض ۴۴",
                    example = "44")
            @DecimalMin(value = "0.01", message = "{contract.working_hours.invalid}")
            BigDecimal workingHoursPerWeek,

            @Schema(description = "واحد پول (ISO). پیش‌فرض IRR", example = "IRR")
            @Pattern(regexp = "^[A-Z]{3}$") String currency,

            @Schema(description = "تاریخ شروع قرارداد (میلادی)", example = "2026-03-21",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate startDate,

            @Schema(description = "تاریخ پایان. خالی یعنی بدون سررسید — فقط برای قرارداد دائم مجاز است",
                    example = "2027-03-20")
            LocalDate endDate,

            @Schema(description = "تاریخ امضای قرارداد — ممکن است با تاریخ شروع متفاوت باشد",
                    example = "2026-03-15")
            LocalDate signedDate,

            @Schema(description = "پایان دوره آزمایشی", example = "2026-04-21")
            LocalDate probationEndDate,

            @Schema(description = "سمت در این قرارداد — ممکن است با سمت عمومی کارمند متفاوت باشد",
                    example = "جوشکار درجه ۲")
            @Size(max = 100) String jobTitle,

            @Schema(description = "شناسه قرارداد قبلی — فقط وقتی این قرارداد جایگزین یک قرارداد خاتمه‌یافته است")
            Long previousContractId,

            @Schema(description = "بندهای خاص قرارداد (JSONB) — برای مواردی که ستون اختصاصی ندارند")
            Map<String, Object> terms,

            @Schema(description = "یادداشت داخلی") @Size(max = 4000) String notes
    ) {}

    @Schema(description = """
            ویرایش قرارداد — فقط notes، terms و jobTitle قابل تغییرند.
            برای تغییر مبلغ یا تاریخ، قرارداد را خاتمه داده و قرارداد جدید بسازید.
            فیلدهای ارسال‌نشده دست‌نخورده می‌مانند.
            """)
    public record UpdateContractRequest(
            @Schema(description = "یادداشت داخلی") @Size(max = 4000) String notes,
            @Schema(description = "بندهای خاص قرارداد (JSONB)") Map<String, Object> terms,
            @Schema(description = "سمت شغلی") @Size(max = 100) String jobTitle
    ) {}

    @Schema(description = "درخواست پایان طبیعی قرارداد")
    public record EndContractRequest(
            @Schema(description = "تاریخ پایان — نمی‌تواند پیش از تاریخ شروع قرارداد باشد",
                    example = "2026-09-22", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate endDate
    ) {}

    @Schema(description = "درخواست باطل کردن قرارداد (اشتباه ثبت)")
    public record VoidContractRequest(
            @Schema(description = "دلیل ابطال", example = "اشتباه در ثبت مبلغ حقوق",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 500) String reason
    ) {}

    @Schema(description = "یک کارگر روی یک پروژه — خلاصه کارمند به‌همراه قرارداد همان پروژه")
    public record ProjectEmployeeResponse(
            @Schema(description = "شناسه کارمند") Long employeeId,
            @Schema(description = "کد پرسنلی", example = "EMP-1-0042") String personnelCode,
            String firstName,
            String lastName,
            @Schema(description = "کد ملی") String nationalId,
            @Schema(description = "شماره تماس") String phoneNumber,

            @Schema(description = "شماره قرارداد روی این پروژه", example = "CT-1-0042") String contractNumber,
            @Schema(description = "سمت در این قرارداد — اگر خالی باشد، سمت عمومی کارمند") String jobTitle,
            @Schema(description = "نوع قرارداد") ContractType contractType,
            @Schema(description = "مبنای حقوق") SalaryBasis salaryBasis,
            @Schema(description = "حقوق پایه") BigDecimal baseSalary,
            @Schema(description = "تاریخ شروع قرارداد") LocalDate startDate,
            @Schema(description = "تاریخ پایان قرارداد — خالی یعنی بدون سررسید") LocalDate endDate,
            @Schema(description = "آیا این قرارداد امروز فعال است") boolean active
    ) {
        public static ProjectEmployeeResponse of(Contract c, ir.manaz.payroll.employee.Employee e) {
            return new ProjectEmployeeResponse(
                    e.getId(), e.getPersonnelCode(), e.getFirstName(), e.getLastName(),
                    e.getNationalId(), e.getPhoneNumber(),
                    c.getContractNumber(),
                    c.getJobTitle() != null ? c.getJobTitle() : e.getJobTitle(),
                    c.getContractType(), c.getSalaryBasis(), c.getBaseSalary(),
                    c.getStartDate(), c.getEndDate(),
                    c.isActiveAsOf(LocalDate.now())
            );
        }

        /** قراردادی که کارمندش حذف نرم شده — داده هویتی در دسترس نیست. */
        public static ProjectEmployeeResponse deletedEmployee(Contract c) {
            return new ProjectEmployeeResponse(
                    c.getEmployeeId(), null, null, null, null, null,
                    c.getContractNumber(), c.getJobTitle(),
                    c.getContractType(), c.getSalaryBasis(), c.getBaseSalary(),
                    c.getStartDate(), c.getEndDate(),
                    c.isActiveAsOf(LocalDate.now())
            );
        }
    }

    @Schema(description = "اطلاعات کامل یک قرارداد")
    public record ContractResponse(
            Long id,
            @Schema(description = "شماره قرارداد", example = "CT-1-0042") String contractNumber,
            Long employeeId,
            Long projectId,

            ContractType contractType,
            SalaryBasis salaryBasis,

            BigDecimal baseSalary,
            BigDecimal housingAllowance,
            BigDecimal foodAllowance,
            BigDecimal childAllowanceBase,
            BigDecimal transportAllowance,
            BigDecimal seniorityPay,
            BigDecimal hardshipAllowance,
            @Schema(description = "مجموع مزایای ثابت — بدون حقوق پایه و حق اولاد")
            BigDecimal totalFixedAllowances,

            BigDecimal workingHoursPerWeek,
            String currency,

            LocalDate startDate,
            @Schema(description = "تاریخ پایان. null یعنی بدون سررسید") LocalDate endDate,
            LocalDate signedDate,
            LocalDate probationEndDate,
            String jobTitle,

            @Schema(description = "آیا این قرارداد امروز فعال است") boolean active,

            Long previousContractId,
            Map<String, Object> terms,
            String notes,

            boolean voided,
            Instant voidedAt,
            String voidReason,

            Instant createdAt,
            Instant updatedAt
    ) {
        public static ContractResponse from(Contract c) {
            return new ContractResponse(
                    c.getId(), c.getContractNumber(), c.getEmployeeId(), c.getProjectId(),
                    c.getContractType(), c.getSalaryBasis(),
                    c.getBaseSalary(), c.getHousingAllowance(), c.getFoodAllowance(),
                    c.getChildAllowanceBase(), c.getTransportAllowance(),
                    c.getSeniorityPay(), c.getHardshipAllowance(),
                    c.totalFixedAllowances(),
                    c.getWorkingHoursPerWeek(), c.getCurrency(),
                    c.getStartDate(), c.getEndDate(),
                    c.getSignedDate(), c.getProbationEndDate(), c.getJobTitle(),
                    c.isActiveAsOf(LocalDate.now()),
                    c.getPreviousContractId(), c.getTerms(), c.getNotes(),
                    c.isVoided(), c.getVoidedAt(), c.getVoidReason(),
                    c.getCreatedAt(), c.getUpdatedAt()
            );
        }
    }
}