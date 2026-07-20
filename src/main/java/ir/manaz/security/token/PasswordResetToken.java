package ir.manaz.security.token;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_pwd_reset_token_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_pwd_reset_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 hash of the raw token - the raw one is only sent to the user. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean used = false;

    @Column(name = "used_at")
    private Instant usedAt;

    public boolean isValid() {
        return !used && Instant.now().isBefore(expiresAt);
    }
}
