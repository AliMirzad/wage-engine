package ir.manaz.payroll.employee;

import ir.manaz.common.BaseEntity;
import ir.manaz.common.SoftDeletable;
import ir.manaz.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A person on the tenant's payroll. Never hard-deleted; when removed the
 * {@code deletedAt / deletedBy} pair is set and the row is filtered out of all
 * default queries via {@link SQLRestriction}.
 * <p>
 * Uniqueness rules:
 * <ul>
 *   <li>{@code (tenantId, personnelCode)} — globally unique (enforced by table constraint).</li>
 *   <li>{@code (tenantId, nationalId)} — unique among non-deleted rows only,
 *       so a rehired employee gets a fresh record.</li>
 * </ul>
 */
@Entity
@Table(name = "employees", uniqueConstraints = {
        @UniqueConstraint(name = "uk_employee_tenant_personnel_code",
                columnNames = {"tenant_id", "personnel_code"})
})
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Employee extends BaseEntity implements TenantAware, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Auto-generated: {@code EMP-{tenantId}-{sequence}}. */
    @Column(name = "personnel_code", nullable = false, length = 32)
    private String personnelCode;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /** Iranian national ID (10 digits). Validated at DTO layer. */
    @Column(name = "national_id", nullable = false, length = 10)
    private String nationalId;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "hire_date", nullable = false)
    private LocalDate hireDate;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(length = 255)
    private String email;

    @Column(name = "children_count", nullable = false)
    @Builder.Default
    private Integer childrenCount = 0;

    /** IBAN (شماره شبا) — IR + 24 digits. */
    @Column(length = 26)
    private String iban;

    // ─── مؤثر در محاسبه حقوق ─────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", nullable = false, length = 20)
    @Builder.Default
    private MaritalStatus maritalStatus = MaritalStatus.SINGLE;

    /** شماره بیمه تأمین اجتماعی — بدون آن ارسال لیست بیمه ممکن نیست */
    @Column(name = "insurance_number", length = 20)
    private String insuranceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurance_type", nullable = false, length = 20)
    @Builder.Default
    private InsuranceType insuranceType = InsuranceType.MANDATORY;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.FULL_TIME;

    /** سمت شغلی — روی فیش حقوقی و گروه‌بندی بیمه اثر دارد */
    @Column(name = "job_title", length = 100)
    private String jobTitle;

    // ─── پرونده پرسنلی ───────────────────────────────────────

    @Column(name = "father_name", length = 100)
    private String fatherName;

    /** شماره شناسنامه */
    @Column(name = "id_card_number", length = 20)
    private String idCardNumber;

    /** محل صدور شناسنامه */
    @Column(name = "issue_place", length = 100)
    private String issuePlace;

    @Enumerated(EnumType.STRING)
    @Column(name = "military_status", length = 20)
    private MilitaryStatus militaryStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "education_level", length = 50)
    private EducationLevel educationLevel;

    @Column(length = 500)
    private String address;

    @Column(name = "emergency_contact_name", length = 100)
    private String emergencyContactName;

    @Column(name = "emergency_contact_phone", length = 20)
    private String emergencyContactPhone;

    /** نام بانک — کنار شبا برای تولید فایل بانکی */
    @Column(name = "bank_name", length = 100)
    private String bankName;

    // ─── ترک کار ─────────────────────────────────────────────

    /**
     * تاریخ ترک کار. متفاوت با {@code deletedAt}:
     * این یعنی «از کار خارج شد» و رکورد باید بماند (برای سنوات و تسویه)،
     * در حالی که {@code deletedAt} یعنی «اشتباه ثبت شده بود».
     */
    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Column(name = "termination_reason", length = 500)
    private String terminationReason;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;
}
