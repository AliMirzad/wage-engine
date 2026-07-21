package ir.manaz.security.permission;

import ir.manaz.security.role.Permission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Auto-inserts any {@link Permission} enum value missing from the {@code permissions} table.
 *
 * <p>This is a developer convenience: when someone adds a new enum value, they don't need
 * to also write a Flyway migration for the metadata row. On next startup, this runner
 * inserts a placeholder row (description = code, category = inferred from prefix), and
 * admin can edit description/category via UI.
 *
 * <p>This does NOT assign new permissions to any role — role-permission assignment is
 * always admin-driven or via explicit Flyway migration.
 */
@Slf4j
@Component
@Order(0)   // BEFORE DefaultRoles(@Order(1)) and SuperAdminSeeder(@Order(2))
@RequiredArgsConstructor
public class PermissionCatalogSync implements CommandLineRunner {

    private final PermissionRepository repo;

    @Override
    public void run(String... args) {
        Set<String> existing = repo.findAll().stream()
                .map(PermissionDefinition::getCode)
                .collect(Collectors.toSet());

        int added = 0;
        for (Permission p : Permission.values()) {
            if (!existing.contains(p.name())) {
                repo.save(PermissionDefinition.builder()
                        .code(p.name())
                        .descriptionFa(p.name())  // placeholder; admin edits later
                        .category(inferCategory(p.name()))
                        .build());
                added++;
                log.info("Auto-seeded new permission: {}", p.name());
            }
        }
        if (added > 0) {
            log.info("Permission catalog: {} new entries added", added);
        }
    }

    private String inferCategory(String code) {
        int idx = code.indexOf('_');
        return idx > 0 ? code.substring(0, idx) : "OTHER";
    }
}
