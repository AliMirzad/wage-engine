package ir.manaz.security.user;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * فقط برای تولید claimهای توکن استفاده می‌شود — هرگز وارد SecurityContext نمی‌شود.
 * فیلتر JWT به‌جای این، AuthenticatedPrincipal را در context می‌گذارد.
 * <p>
 * چون AuthService احراز هویت را دستی انجام می‌دهد (بدون AuthenticationManager)،
 * قراردادهای UserDetails مثل isAccountNonLocked/isEnabled به‌طور خودکار اعمال
 * نمی‌شوند. نگهبان واقعی قفل حساب {@code LoginAttemptService.isLocked} است
 * و بررسی فعال بودن حساب مستقیماً در {@code AuthService.login} انجام می‌شود.
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final Long tenantId;
    private final String username;
    private final String password;
    private final String email;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.id = user.getId();
        this.tenantId = user.getTenantId();
        this.username = user.getUsername();
        this.password = user.getPasswordHash();
        this.email = user.getEmail();
        this.enabled = user.isEnabled();
        this.accountNonLocked = isAccountStillValid(user);
        this.authorities = buildAuthorities(user);
    }

    private static boolean isAccountStillValid(User user) {
        if (!user.isAccountNonLocked()) {
            Instant until = user.getLockedUntil();
            return until != null && until.isBefore(Instant.now());
        }
        return true;
    }

    private static Set<GrantedAuthority> buildAuthorities(User user) {
        Set<GrantedAuthority> auths = new HashSet<>();
        user.getRoles().forEach(role -> {
            auths.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
            auths.addAll(role.getPermissions().stream()
                    .map(p -> new SimpleGrantedAuthority(p.authority()))
                    .collect(Collectors.toSet()));
        });
        return auths;
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
