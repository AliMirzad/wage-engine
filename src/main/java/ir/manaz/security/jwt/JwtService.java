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

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(base64EncodeIfNeeded(props.getJwt().getSecret()));
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Allow either base64 or plain secret; encode if plain (>= 32 bytes required).
     */
    private String base64EncodeIfNeeded(String secret) {
        try {
            byte[] decoded = Decoders.BASE64.decode(secret);
            if (decoded.length >= 32) return secret;
        } catch (Exception ignored) { }
        return java.util.Base64.getEncoder().encodeToString(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateAccessToken(CustomUserDetails user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(props.getJwt().getAccessTokenExpiration());

        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.getUsername())
                .issuer(props.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        CLAIM_USER_ID, user.getId(),
                        CLAIM_TENANT_ID, user.getTenantId() == null ? -1L : user.getTenantId(),
                        CLAIM_AUTHORITIES, authorities,
                        CLAIM_TOKEN_TYPE, TYPE_ACCESS
                ))
                .signWith(signingKey())
                .compact();
    }

    public String generateRefreshToken(CustomUserDetails user, String jti) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(props.getJwt().getRefreshTokenExpiration());

        return Jwts.builder()
                .id(jti)
                .subject(user.getUsername())
                .issuer(props.getJwt().getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claims(Map.of(
                        CLAIM_USER_ID, user.getId(),
                        CLAIM_TENANT_ID, user.getTenantId() == null ? -1L : user.getTenantId(),
                        CLAIM_TOKEN_TYPE, TYPE_REFRESH
                ))
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
