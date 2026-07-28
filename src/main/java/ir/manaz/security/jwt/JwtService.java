package ir.manaz.security.jwt;

import ir.manaz.config.AppSecurityProperties;
import ir.manaz.security.user.CustomUserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_TENANT_ID = "tid";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_AUTHORITIES = "auth";
    public static final String CLAIM_TOKEN_TYPE = "type";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final AppSecurityProperties props;

    private volatile SecretKey cachedKey;

    private SecretKey signingKey() {
        SecretKey key = cachedKey;
        if (key != null) return key;
        synchronized (this) {
            if (cachedKey == null) {
                cachedKey = buildKey(props.getJwt().getSecret());
            }
            return cachedKey;
        }
    }

    /**
     * secret باید Base64 با حداقل ۳۲ بایت decode شده باشد. رشته plain رد می‌شود
     * تا اپراتور به‌جای «هرچیزی که ۳۲ کاراکتر بود» صریح یک secret قوی تولید کند:
     * {@code openssl rand -base64 48}
     */
    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("app.security.jwt.secret is required");
        }
        byte[] decoded;
        try {
            decoded = Decoders.BASE64.decode(secret);
        } catch (Exception ex) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be Base64. Generate with: openssl rand -base64 48", ex);
        }
        if (decoded.length < 32) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must decode to at least 32 bytes (256 bit). " +
                    "Current size: " + decoded.length + " bytes.");
        }
        return Keys.hmacShaKeyFor(decoded);
    }

    public String generateAccessToken(CustomUserDetails user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(props.getJwt().getAccessTokenExpiration());

        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getId());
        if (user.getTenantId() != null) claims.put(CLAIM_TENANT_ID, user.getTenantId());
        claims.put(CLAIM_AUTHORITIES, authorities);
        claims.put(CLAIM_TOKEN_TYPE, TYPE_ACCESS);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(props.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(claims)
                .signWith(signingKey())
                .compact();
    }

    public String generateRefreshToken(CustomUserDetails user, String jti) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(props.getJwt().getRefreshTokenExpiration());

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, user.getId());
        if (user.getTenantId() != null) claims.put(CLAIM_TENANT_ID, user.getTenantId());
        claims.put(CLAIM_TOKEN_TYPE, TYPE_REFRESH);

        return Jwts.builder()
                .id(jti)
                .subject(user.getUsername())
                .issuer(props.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(claims)
                .signWith(signingKey())
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey())
                .requireIssuer(props.getJwt().getIssuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class));
    }
}
