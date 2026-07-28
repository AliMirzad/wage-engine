package ir.manaz.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
@Slf4j
public class AppSecurityProperties {

    private Jwt jwt = new Jwt();
    private Password password = new Password();
    private LoginAttempt loginAttempt = new LoginAttempt();
    private Cors cors = new Cors();
    private RateLimit rateLimit = new RateLimit();
    private Cookie cookie = new Cookie();


    @Getter @Setter
    public static class Jwt {
        private String secret;
        private long accessTokenExpiration = 900_000L;
        private long refreshTokenExpiration = 604_800_000L;
        private String issuer = "accounting-app";
    }

    @Getter @Setter
    public static class Password {
        private int minLength = 8;
        private boolean requireLetter = true;
        private boolean requireDigit = true;
    }

    @Getter @Setter
    public static class LoginAttempt {
        private int maxAttempts = 5;
        private int lockDurationMinutes = 15;
    }

    @Getter @Setter
    public static class RateLimit {
        /** سقف کل درخواست‌های auth از هر IP در دقیقه (login/refresh/reset-password). */
        private int authPerMinute = 20;
        /** سقف مخصوص forgot-password در دقیقه — سخت‌گیرانه‌تر چون قابل abuse برای spam. */
        private int forgotPasswordPerMinute = 5;
    }

    /**
     * httpOnly auth cookies. For panel on manaz.pro + API on api.manaz.pro use
     * domain=.manaz.pro, sameSite=None, secure=true (via env).
     */
    @Getter @Setter
    public static class Cookie {
        private boolean enabled = true;
        private String accessName = "manaz_access";
        private String refreshName = "manaz_refresh";
        private String accessPath = "/api";
        private String refreshPath = "/api/v1/auth";
        /** Empty = host-only cookie. Cross-subdomain: {@code .manaz.pro}. */
        private String domain = "";
        private boolean secure = true;
        private String sameSite = "Lax";
    }

    @Getter @Setter
    public static class Cors {
        private boolean enabled = true;
        /**
         * Comma-separated exact origins (no wildcards), e.g.
         * {@code https://manaz.pro} or {@code https://manaz.pro,http://localhost:5173}.
         */
        private String allowedOrigins = "";
        private List<String> allowedMethods =
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders =
                List.of("Authorization", "Content-Type", "Accept", "X-Requested-With");
        private long maxAge = 3600;

        public List<String> allowedOriginList() {
            if (allowedOrigins == null || allowedOrigins.isBlank()) {
                return List.of();
            }
            return java.util.Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
    }
}
