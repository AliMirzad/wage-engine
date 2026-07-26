package ir.manaz.payroll.contract;

import ir.manaz.common.BaseEntity;
import ir.manaz.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * A contract binds one {@code Employee} to one {@code Project} with a specific
 * financial arrangement. Contracts are historical records — never hard-deleted.
 * <p>
 * Lifecycle:
 * <ul>
 *   <li><b>Active</b>: {@code voided = false} AND ({@code endDate = null} OR {@code endDate >= today}).</li>
 *   <li><b>Ended (natural)</b>: set {@code endDate} to a past date. Row remains for history.</li>
 *   <li><b>Voided</b> (data-entry mistake): set {@code voided = true} + {@code voidedAt/By/Reason}.
 *       Voided contracts are excluded from payroll calculations.</li>
 * </ul>
 * <p>
 * All monetary amounts are stored as {@link BigDecimal} with precision (19,4).
 * Never use {@code double} or {@code float} for money.
 * <p>
 * The {@code terms} JSONB column holds contract-specific fields whose shape is
 * still being discovered (e.g. custom allowances, contract type). Fields that
 * become universal should be promoted to real columns in a later migration.
 */
@Entity
@Table(name = "contracts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_contract_tenant_number",
                columnNames = {"tenant_id", "contract_number"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Contract extends BaseEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    /** Auto-generated: {@code CNT-{tenantId}-{sequence}}. */
    @Column(name = "contract_number", nullable = false, length = 64)
    private String contractNumber;

    // ---------- financial fields (BigDecimal only) ----------

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    private BigDecimal baseSalary;

    @Column(name = "housing_allowance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal housingAllowance = BigDecimal.ZERO;

    @Column(name = "food_allowance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal foodAllowance = BigDecimal.ZERO;

    @Column(name = "child_allowance_base", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal childAllowanceBase = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "IRR";

    /**
     * مبنای محاسبه حقوق پایه — تعیین می‌کند baseSalary در چه ضرب شود.
     * MONTHLY: ثابت ماهانه، DAILY: × روزهای کارکرد، HOURLY: × ساعات کارکرد.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "salary_basis", nullable = false, length = 20)
    @Builder.Default
    private SalaryBasis salaryBasis = SalaryBasis.MONTHLY;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 20)
    @Builder.Default
    private ContractType contractType = ContractType.TEMPORARY;

    /** حق ایاب و ذهاب */
    @Column(name = "transport_allowance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal transportAllowance = BigDecimal.ZERO;

    /** پایه سنوات — قانوناً برای سابقه بیش از یک سال الزامی است */
    @Column(name = "seniority_pay", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal seniorityPay = BigDecimal.ZERO;

    /** حق بدی آب و هوا / سختی کار */
    @Column(name = "hardship_allowance", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal hardshipAllowance = BigDecimal.ZERO;

    /** ساعات کار هفتگی — مبنای محاسبه اضافه‌کاری. سقف قانونی ۴۴ ساعت. */
    @Column(name = "working_hours_per_week", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal workingHoursPerWeek = new BigDecimal("44");

    /** تاریخ امضای قرارداد — ممکن است با تاریخ شروع متفاوت باشد */
    @Column(name = "signed_date")
    private LocalDate signedDate;

    /** پایان دوره آزمایشی */
    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    /** سمت در این قرارداد — ممکن است با سمت عمومی کارمند متفاوت باشد */
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // ---------- validity window ----------

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** {@code null} = open-ended (still active). */
    @Column(name = "end_date")
    private LocalDate endDate;

    // ---------- extensibility ----------

    /** Flexible bag for contract-specific terms. Backed by PostgreSQL JSONB. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "terms", columnDefinition = "jsonb")
    private Map<String, Object> terms;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // ---------- void (data-entry mistake) ----------

    @Column(nullable = false)
    @Builder.Default
    private boolean voided = false;

    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", columnDefinition = "TEXT")
    private String voidReason;

    @Column(name = "previous_contract_id")
    private Long previousContractId;

// ---------- derived ----------

    /**
     * تعریف مرجع «قرارداد فعال». محاسبه‌شونده، ذخیره نمی‌شود.
     * <p>
     * توجه: کوئری‌های {@code findActiveContractNumbersBy...} همین منطق را در SQL
     * تکرار می‌کنند — هر تغییری اینجا باید آنجا هم اعمال شود.
     */
    public boolean isActiveAsOf(LocalDate date) {
        if (voided) return false;
        if (startDate.isAfter(date)) return false;
        return endDate == null || !endDate.isBefore(date);
    }

    /** مجموع مزایای ثابت ماهانه (بدون حقوق پایه). */
    public BigDecimal totalFixedAllowances() {
        return housingAllowance
                .add(foodAllowance)
                .add(transportAllowance)
                .add(seniorityPay)
                .add(hardshipAllowance);
    }
}
