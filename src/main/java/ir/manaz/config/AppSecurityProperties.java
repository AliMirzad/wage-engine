package ir.manaz.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security")
@Slf4j
public class AppSecurityProperties {

    private Jwt jwt = new Jwt();
    private Password password = new Password();
    private LoginAttempt loginAttempt = new LoginAttempt();
    private PasswordReset passwordReset = new PasswordReset();
    private Cors cors = new Cors();


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
    public static class PasswordReset {
        private int tokenExpirationMinutes = 30;
    }

    @Getter @Setter
    public static class Cors {
        private boolean enabled = true;
        /** Exact origins, e.g. https://panel.manaz.pro — wildcards are rejected. */
        private List<String> allowedOrigins = new ArrayList<>();
        private List<String> allowedMethods =
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        private List<String> allowedHeaders =
                List.of("Authorization", "Content-Type", "Accept", "X-Requested-With");
        private long maxAge = 3600;
    }
}
