package ir.manaz.payroll.project;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    @Schema(description = "ثبت پروژه جدید")
    public record CreateProjectRequest(
            @Schema(description = "نام پروژه", example = "مجتمع مسکونی نارون",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 255) String name,

            @Schema(description = "کد یکتای پروژه در شرکت — پس از ثبت قابل تغییر نیست", example = "P-001",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank @Size(max = 64) String code,

            @Schema(description = "شرح پروژه") String description,

            @Schema(description = "تاریخ شروع پروژه", example = "2026-01-15",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull LocalDate startDate,

            @Schema(description = "تاریخ پایان پیش‌بینی‌شده. خالی یعنی نامحدود", example = "2027-06-30")
            LocalDate endDate,

            @Schema(description = "نام کارفرما", example = "شرکت عمران گستر") @Size(max = 200) String clientName,
            @Schema(description = "شناسه ملی کارفرما", example = "10101234567") @Size(max = 20) String clientNationalId,
            @Schema(description = "شماره پیمان با کارفرما", example = "PMN-1404-88") @Size(max = 64) String clientContractNumber,
            @Schema(description = "تاریخ انعقاد پیمان", example = "2026-01-05") LocalDate clientContractDate,

            @Schema(description = "مبلغ کل پیمان (ریال). نیازمند دسترسی PROJECT_FINANCIAL_READ — بدون آن نادیده گرفته می‌شود",
                    example = "125000000000")
            @DecimalMin(value = "0", inclusive = false, message = "{project.contract_amount.invalid}")
            BigDecimal contractAmount,

            @Schema(description = "محل اجرا", example = "تهران، شهرک غرب") @Size(max = 255) String location,
            @Schema(description = "یادداشت") String notes
    ) {
    }

    @Schema(description = "ویرایش پروژه — کد پروژه و وضعیت از این مسیر قابل تغییر نیستند")
    public record UpdateProjectRequest(
            @Schema(description = "نام پروژه") @Size(max = 255) String name,
            @Schema(description = "شرح پروژه") String description,
            @Schema(description = "تاریخ شروع") LocalDate startDate,
            @Schema(description = "تاریخ پایان پیش‌بینی‌شده") LocalDate endDate,
            @Schema(description = "نام کارفرما") @Size(max = 200) String clientName,
            @Schema(description = "شناسه ملی کارفرما") @Size(max = 20) String clientNationalId,
            @Schema(description = "شماره پیمان با کارفرما") @Size(max = 64) String clientContractNumber,
            @Schema(description = "تاریخ انعقاد پیمان") LocalDate clientContractDate,

            @Schema(description = "مبلغ کل پیمان. نیازمند PROJECT_FINANCIAL_READ — بدون آن نادیده گرفته می‌شود")
            @DecimalMin(value = "0", inclusive = false, message = "{project.contract_amount.invalid}")
            BigDecimal contractAmount,

            @Schema(description = "محل اجرا") @Size(max = 255) String location,
            @Schema(description = "یادداشت") String notes
    ) {
    }

    @Schema(description = "تغییر وضعیت پروژه")
    public record ChangeStatusRequest(
            @Schema(description = "وضعیت مقصد", example = "ACTIVE",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ProjectStatus status,

            @Schema(description = "تاریخ پایان واقعی — فقط برای گذار به COMPLETED یا CANCELLED. خالی یعنی امروز",
                    example = "2027-05-20")
            LocalDate actualEndDate,

            @Schema(description = "دلیل تغییر وضعیت — برای ثبت در تاریخچه") @Size(max = 500) String reason
    ) {
    }

    @Schema(description = "اطلاعات پروژه")
    public record ProjectResponse(
            Long id,
            String name,
            String code,
            String description,
            @Schema(description = "PLANNED / ACTIVE / SUSPENDED / COMPLETED / CANCELLED") ProjectStatus status,
            @Schema(description = "آیا در این وضعیت ثبت قرارداد جدید ممکن است") boolean acceptsNewContracts,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate actualEndDate,
            String clientName,
            String clientNationalId,
            String clientContractNumber,
            LocalDate clientContractDate,
            @Schema(description = "مبلغ کل پیمان. برای کاربران بدون PROJECT_FINANCIAL_READ همیشه null است")
            BigDecimal contractAmount,
            String location,
            String notes,
            @Schema(description = "زمان خاتمه/لغو پروژه") Instant closedAt,
            Long closedBy,
            @Schema(description = "وضعیت‌هایی که از وضعیت فعلی می‌توان به آن‌ها رفت — فرانت همین را در دراپ‌داون نمایش دهد",
                    example = "[\"SUSPENDED\",\"COMPLETED\",\"CANCELLED\"]")
            Set<ProjectStatus> availableTransitions
    ) {
        /**
         * @param includeFinancial اگر false باشد، مبلغ پیمان از پاسخ حذف می‌شود.
         */
        public static ProjectResponse from(Project p, boolean includeFinancial) {
            return new ProjectResponse(
                    p.getId(), p.getName(), p.getCode(), p.getDescription(),
                    p.getStatus(), p.getStatus().allowsNewContracts(),
                    p.getStartDate(), p.getEndDate(), p.getActualEndDate(),
                    p.getClientName(), p.getClientNationalId(),
                    p.getClientContractNumber(), p.getClientContractDate(),
                    includeFinancial ? p.getContractAmount() : null,
                    p.getLocation(), p.getNotes(),
                    p.getClosedAt(), p.getClosedBy(),
                    p.getStatus().allowedTransitions()
            );
        }
    }
}