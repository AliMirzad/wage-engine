package ir.manaz.payroll.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * DTOهای قرارداد. تمام فیلدهای مالی BigDecimal هستند.
 * شماره قرارداد سرور تولید می‌کند (CT-{tenantId}-{4-digit-seq}).
 * تمام فیلدها به‌جز notes و terms پس از ایجاد تغییرناپذیر هستند —
 * برای تغییر حقوق یا تاریخ، این قرارداد را end کنید و قرارداد جدید بسازید
 * (با previousContractId اشاره به این).
 */
public final class ContractDtos {

    private ContractDtos() {}

    @Schema(description = "درخواست ایجاد قرارداد جدید")
    public record CreateContractRequest(
            @Schema(description = "شناسه کارمند", example = "42", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            Long employeeId,

            @Schema(description = "شناسه پروژه", example = "7", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            Long projectId,

            @Schema(description = "حقوق پایه (ریال یا واحد پول)", example = "50000000", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            @DecimalMin(value = "0.0001", message = "{contract.base_salary.non_positive}")
            BigDecimal baseSalary,

            @Schema(description = "حق مسکن", example = "9000000")
            @DecimalMin(value = "0")
            BigDecimal housingAllowance,

            @Schema(description = "حق خواروبار (بن)", example = "14000000")
            @DecimalMin(value = "0")
            BigDecimal foodAllowance,

            @Schema(description = "حق اولاد پایه (به ازای هر فرزند)", example = "5000000")
            @DecimalMin(value = "0")
            BigDecimal childAllowanceBase,

            @Schema(description = "واحد پول (ISO)", example = "IRR")
            @Pattern(regexp = "^[A-Z]{3}$")
            String currency,

            @Schema(description = "تاریخ شروع قرارداد", example = "1404-01-01", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            LocalDate startDate,

            @Schema(description = "تاریخ پایان (اختیاری — خالی یعنی بدون سررسید)", example = "1404-12-29")
            LocalDate endDate,

            @Schema(description = "شناسه قرارداد قبلی — فقط وقتی این قرارداد جایگزین یک قرارداد end شده است")
            Long previousContractId,

            @Schema(description = "بند‌های خاص قرارداد (اختیاری، JSONB)")
            Map<String, Object> terms,

            @Schema(description = "یادداشت داخلی")
            @Size(max = 4000)
            String notes
    ) {}

    @Schema(description = "ویرایش قرارداد — فقط notes و terms قابل تغییرند. برای تغییر مبلغ یا تاریخ، قرارداد را end کرده و قرارداد جدید بسازید.")
    public record UpdateContractRequest(
            @Schema(description = "یادداشت داخلی")
            @Size(max = 4000)
            String notes,

            @Schema(description = "بند‌های خاص قرارداد (JSONB)")
            Map<String, Object> terms
    ) {}

    @Schema(description = "درخواست پایان طبیعی قرارداد")
    public record EndContractRequest(
            @Schema(description = "تاریخ پایان — پیش‌فرض امروز", example = "1404-06-31", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            LocalDate endDate
    ) {}

    @Schema(description = "درخواست باطل کردن قرارداد (اشتباه ثبت)")
    public record VoidContractRequest(
            @Schema(description = "دلیل ابطال", example = "اشتباه در ثبت مبلغ حقوق", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 500)
            String reason
    ) {}

    @Schema(description = "اطلاعات کامل یک قرارداد")
    public record ContractResponse(
            @Schema(description = "شناسه داخلی") Long id,
            @Schema(description = "شماره قرارداد", example = "CT-1-0042") String contractNumber,
            @Schema(description = "شناسه کارمند") Long employeeId,
            @Schema(description = "شناسه پروژه") Long projectId,
            @Schema(description = "حقوق پایه") BigDecimal baseSalary,
            @Schema(description = "حق مسکن") BigDecimal housingAllowance,
            @Schema(description = "حق خواروبار") BigDecimal foodAllowance,
            @Schema(description = "حق اولاد پایه") BigDecimal childAllowanceBase,
            @Schema(description = "واحد پول") String currency,
            @Schema(description = "تاریخ شروع") LocalDate startDate,
            @Schema(description = "تاریخ پایان (null = بدون سررسید)") LocalDate endDate,
            @Schema(description = "شناسه قرارداد قبلی (در صورت وجود)") Long previousContractId,
            @Schema(description = "شرایط ویژه") Map<String, Object> terms,
            @Schema(description = "یادداشت") String notes,
            @Schema(description = "باطل شده") boolean voided,
            @Schema(description = "زمان ابطال") Instant voidedAt,
            @Schema(description = "دلیل ابطال") String voidReason,
            @Schema(description = "زمان ایجاد") Instant createdAt,
            @Schema(description = "زمان آخرین ویرایش") Instant updatedAt
    ) {
        public static ContractResponse from(Contract c) {
            return new ContractResponse(
                    c.getId(), c.getContractNumber(), c.getEmployeeId(), c.getProjectId(),
                    c.getBaseSalary(), c.getHousingAllowance(), c.getFoodAllowance(),
                    c.getChildAllowanceBase(), c.getCurrency(),
                    c.getStartDate(), c.getEndDate(), c.getPreviousContractId(),
                    c.getTerms(), c.getNotes(),
                    c.isVoided(), c.getVoidedAt(), c.getVoidReason(),
                    c.getCreatedAt(), c.getUpdatedAt()
            );
        }
    }
}