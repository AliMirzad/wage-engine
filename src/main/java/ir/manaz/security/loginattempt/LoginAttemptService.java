package ir.manaz.security.loginattempt;

import ir.manaz.config.AppSecurityProperties;
import ir.manaz.security.user.User;
import ir.manaz.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AppSecurityProperties props;

    @Transactional
    public void loginFailed(String usernameOrEmail) {
        userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .ifPresent(this::increment);
    }

    private void increment(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);

        int max = props.getLoginAttempt().getMaxAttempts();
        if (attempts >= max) {
            user.setAccountNonLocked(false);
            user.setLockedUntil(Instant.now().plus(
                    props.getLoginAttempt().getLockDurationMinutes(), ChronoUnit.MINUTES));
            log.warn("User {} locked after {} failed attempts", user.getUsername(), attempts);
        }
        userRepository.save(user);
    }

    @Transactional
    public void loginSucceeded(String usernameOrEmail) {
        userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .ifPresent(u -> {
                    u.setFailedLoginAttempts(0);
                    u.setAccountNonLocked(true);
                    u.setLockedUntil(null);
                    u.setLastLoginAt(Instant.now());
                    userRepository.save(u);
                });
    }

    @Transactional(readOnly = true)
    public boolean isLocked(String usernameOrEmail) {
        return userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .map(u -> {
                    if (u.isAccountNonLocked()) return false;
                    if (u.getLockedUntil() == null) return true;
                    return u.getLockedUntil().isAfter(Instant.now());
                })
                .orElse(false);
    }
}
