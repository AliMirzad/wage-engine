package ir.manaz.security.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameAndTenantId(String username, Long tenantId);

    boolean existsByUsernameAndTenantId(String username, Long tenantId);

    boolean existsByEmailAndTenantId(String email, Long tenantId);
}
