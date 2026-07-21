package ir.manaz.security.permission;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionDefinition, Long> {

    Optional<PermissionDefinition> findByCode(String code);

    List<PermissionDefinition> findAllByOrderByCategoryAscCodeAsc();

    boolean existsByCode(String code);
}
