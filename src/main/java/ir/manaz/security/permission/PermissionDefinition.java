package ir.manaz.security.permission;

import ir.manaz.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Metadata for a permission code.
 *
 * <p>The set of codes is defined by {@link ir.manaz.security.role.Permission} enum
 * (source of truth, since {@code @PreAuthorize("hasAuthority('...')")} references
 * it at compile time). This table stores the Persian label and UI category,
 * both editable by admin at runtime.
 */
@Entity
@Table(name = "permissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionDefinition extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Matches {@link ir.manaz.security.role.Permission} enum name. */
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "description_fa", nullable = false, length = 255)
    private String descriptionFa;

    @Column(nullable = false, length = 50)
    private String category;
}
