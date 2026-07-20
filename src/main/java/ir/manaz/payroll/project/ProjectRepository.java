package ir.manaz.payroll.project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    Optional<Project> findByIdAndTenantId(Long id, Long tenantId);

    Page<Project> findByTenantId(Long tenantId, Pageable pageable);

    Page<Project> findByTenantIdAndActive(Long tenantId, boolean active, Pageable pageable);

    boolean existsByTenantIdAndCode(Long tenantId, String code);
}
