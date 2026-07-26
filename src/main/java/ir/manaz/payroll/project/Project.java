package ir.manaz.payroll.project;

import ir.manaz.common.BaseEntity;
import ir.manaz.tenant.TenantAware;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A project inside a tenant. Employees are assigned to projects through Contracts;
 * each (employee, project) pair may have at most one active Contract at a time.
 * <p>
 * Projects are never hard-deleted. Ending a project sets {@code active = false}
 * and stamps {@code archivedAt / archivedBy}.
 */
@Entity
@Table(name = "projects", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_tenant_code", columnNames = {"tenant_id", "code"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity implements TenantAware {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.PLANNED;

    /** نام کارفرما */
    @Column(name = "client_name", length = 200)
    private String clientName;

    /** شناسه ملی / کد اقتصادی کارفرما */
    @Column(name = "client_national_id", length = 20)
    private String clientNationalId;

    /** شماره پیمان با کارفرما — متفاوت با contractNumber قراردادهای کارگران */
    @Column(name = "client_contract_number", length = 64)
    private String clientContractNumber;

    @Column(name = "client_contract_date")
    private LocalDate clientContractDate;

    /** مبلغ کل پیمان — اطلاعات محرمانه، نیازمند PROJECT_FINANCIAL_READ */
    @Column(name = "contract_amount", precision = 19, scale = 4)
    private BigDecimal contractAmount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** پایان پیش‌بینی‌شده. null یعنی نامحدود. */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** پایان واقعی — هنگام خاتمه یا لغو ثبت می‌شود. */
    @Column(name = "actual_end_date")
    private LocalDate actualEndDate;

    @Column(length = 255)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String notes;

    /** زمان خاتمه یا لغو پروژه */
    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closed_by")
    private Long closedBy;
}
