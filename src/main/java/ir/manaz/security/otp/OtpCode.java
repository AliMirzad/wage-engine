package ir.manaz.security.otp;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "otp_codes", indexes = {
        @Index(name = "idx_otp_user_purpose", columnList = "user_id,purpose"),
        @Index(name = "idx_otp_expires", columnList = "expires_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** SHA-256 hex از کد raw. کد raw فقط داخل ایمیل کاربر ارسال می‌شود. */
    @Column(name = "code_hash", nullable = false, length = 128)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private OtpPurpose purpose;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** null یعنی هنوز مصرف نشده. مقدار زمانی که verify موفق بوده. */
    @Column(name = "consumed_at")
    private Instant consumedAt;

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isExhausted() {
        return attempts >= maxAttempts;
    }

    /** فقط کد فعال قابل verify است. */
    public boolean isVerifiable() {
        return !isConsumed() && !isExpired() && !isExhausted();
    }
}
