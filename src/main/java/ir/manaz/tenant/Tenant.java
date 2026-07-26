package ir.manaz.tenant;

import ir.manaz.common.BaseEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Entity
@Table(name = "tenants", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_code", columnNames = "code")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Business name of the company */
    @NotBlank
    @Size(max = 200)
    @Column(nullable = false, length = 200)
    private String name;

    /** Unique short code used in subdomains or headers, e.g. "acme" */
    @NotBlank
    @Size(max = 50)
    @Column(nullable = false, length = 50)
    private String code;

    @Column(name = "national_id", length = 20)
    private String nationalId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    /** کد کارگاه تأمین اجتماعی — برای ارسال لیست بیمه */
    @Column(name = "insurance_workshop_code", length = 20)
    private String insuranceWorkshopCode;

    /** کد اقتصادی / شناسه مالیاتی — برای لیست مالیات حقوق */
    @Column(name = "economic_code", length = 20)
    private String economicCode;

    /** شبای شرکت — مبدأ پرداخت در فایل پایا/ساتنا */
    @Column(length = 26)
    private String iban;

    @Column(length = 500)
    private String address;

    @Column(length = 20)
    private String phone;
}
