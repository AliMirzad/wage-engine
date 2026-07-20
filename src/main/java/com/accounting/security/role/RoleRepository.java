package com.accounting.security.role;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNameAndTenantIdIsNull(String name);

    Optional<Role> findByNameAndTenantId(String name, Long tenantId);

    List<Role> findAllByTenantIdIsNullOrTenantId(Long tenantId);
}
