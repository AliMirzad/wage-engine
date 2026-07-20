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

    @Transactional(readOnly = true)
    public RefreshToken getActive(String jti) {
        RefreshToken token = repository.findByJti(jti)
                .orElseThrow(() -> new UnauthorizedException("auth.refresh_token.invalid"));
        if (!token.isActive()) {
            throw new UnauthorizedException("auth.refresh_token.invalid");
        }
        return token;
    }

    @Transactional
    public void revoke(String jti) {
        repository.findByJti(jti).ifPresent(t -> {
            t.setRevoked(true);
            t.setRevokedAt(Instant.now());
            repository.save(t);
        });
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
