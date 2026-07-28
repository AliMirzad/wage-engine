package ir.manaz.security.token;

import ir.manaz.config.AppSecurityProperties;
import ir.manaz.exception.UnauthorizedException;
import ir.manaz.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository repository;
    private final AppSecurityProperties props;

    @Transactional
    public RefreshToken create(CustomUserDetails user, String userAgent, String ip) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(props.getJwt().getRefreshTokenExpiration());

        RefreshToken token = RefreshToken.builder()
                .jti(jti)
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .issuedAt(now)
                .expiresAt(expiry)
                .userAgent(userAgent)
                .ipAddress(ip)
                .build();
        return repository.save(token);
    }

    /**
     * revoke ساده — تنها هنگام logout استفاده می‌شود؛ برای rotation از
     * {@link #consumeForRotation(String)} استفاده کنید تا اتمی و race-safe باشد.
     */
    @Transactional
    public void revoke(String jti) {
        repository.revokeIfActive(jti, Instant.now());
    }

    /**
     * revoke اتمی یک refresh token در جریان rotation؛ اگر قبلاً استفاده شده
     * (revoked=true) استثنا می‌اندازد. باعث می‌شود دو refresh همزمان با یک
     * توکن نتوانند هر دو موفق باشند و replay کوتاه‌مدت بلاک شود.
     */
    @Transactional
    public RefreshToken consumeForRotation(String jti) {
        RefreshToken token = repository.findByJti(jti)
                .orElseThrow(() -> new UnauthorizedException("auth.refresh_token.invalid"));
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("auth.refresh_token.invalid");
        }
        int updated = repository.revokeIfActive(jti, Instant.now());
        if (updated == 0) {
            // یا race باخته‌ایم یا این توکن قبلاً استفاده شده — به‌عنوان سیگنال replay
            // همه‌ی session‌های کاربر را باطل می‌کنیم تا جلوی سوءاستفاده گرفته شود.
            log.warn("Refresh token replay detected for user {} — revoking all sessions", token.getUserId());
            repository.revokeAllByUserId(token.getUserId(), Instant.now());
            throw new UnauthorizedException("auth.refresh_token.invalid");
        }
        return token;
    }

    @Transactional
    public void revokeAllForUser(Long userId) {
        int n = repository.revokeAllByUserId(userId, Instant.now());
        log.info("Revoked {} refresh tokens for user {}", n, userId);
    }

    /** Cleanup once a day at 3AM. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpired() {
        int n = repository.deleteAllExpired(Instant.now());
        if (n > 0) log.info("Purged {} expired refresh tokens", n);
    }
}
