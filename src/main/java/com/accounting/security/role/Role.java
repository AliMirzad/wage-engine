package com.accounting.security.role;

import com.accounting.security.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.EnumSet;
import java.util.Set;

@Entity
@Table(name = "roles", uniqueConstraints = {
        @UniqueConstraint(name = "uk_role_tenant_name", columnNames = {"tenant_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * NULL means "system role" (available to all tenants).
     * Non-null means role is scoped to a specific tenant (customizable).
     */
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole = false;

    @ElementCollection(targetClass = Permission.class, fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            foreignKey = @ForeignKey(name = "fk_role_permissions_role")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 50, nullable = false)
    @Builder.Default
    private Set<Permission> permissions = EnumSet.noneOf(Permission.class);
}
