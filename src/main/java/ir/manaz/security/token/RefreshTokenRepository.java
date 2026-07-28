package ir.manaz.security.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByJti(String jti);

    /**
     * revoke اتمی یک توکن فعال — برگردانی ۱ یعنی همین صدا زده‌ی موفق بود،
     * صفر یعنی توکن قبلاً revoke شده یا اصلاً وجود ندارد. برای جلوگیری از
     * race در rotation: دو request همزمان با یک jti نمی‌توانند هر دو موفق باشند.
     */
    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now " +
            "WHERE r.jti = :jti AND r.revoked = false")
    int revokeIfActive(@Param("jti") String jti, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revoked = true, r.revokedAt = :now WHERE r.userId = :userId AND r.revoked = false")
    int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    int deleteAllExpired(@Param("now") Instant now);
}
