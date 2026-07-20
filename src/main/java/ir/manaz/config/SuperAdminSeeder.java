package ir.manaz.config;

import ir.manaz.security.role.DefaultRoles;
import ir.manaz.security.role.Role;
import ir.manaz.security.role.RoleRepository;
import ir.manaz.security.user.User;
import ir.manaz.security.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Seeds a first SUPER_ADMIN user if none exists.
 * Credentials come from application.yml so we never commit plaintext.
 * Runs AFTER DefaultRoles (Order 1) so the role exists.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SuperAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.super-admin.username:admin}")
    private String username;

    @Value("${app.security.super-admin.email:admin@accounting.local}")
    private String email;

    @Value("${app.security.super-admin.password:ChangeMe@123}")
    private String password;

    @Value("${app.security.super-admin.enabled:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) return;
        if (userRepository.findByUsername(username).isPresent()) return;

        Role superAdmin = roleRepository.findByNameAndTenantIdIsNull(DefaultRoles.SUPER_ADMIN)
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role missing"));

        Set<Role> roles = new HashSet<>();
        roles.add(superAdmin);

        User user = User.builder()
                .tenantId(null)                       // platform-level, no tenant
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .firstName("Super")
                .lastName("Admin")
                .enabled(true)
                .accountNonLocked(true)
                .passwordChangedAt(Instant.now())
                .roles(roles)
                .build();
        userRepository.save(user);

        log.warn("=====================================================");
        log.warn(" SUPER_ADMIN user '{}' created.", username);
        log.warn(" Change the default password immediately!");
        log.warn("=====================================================");
    }
}
