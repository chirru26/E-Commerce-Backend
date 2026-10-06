package com.chirru.ecommerce.modules.identity.infrastructure;

import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityUserRepository extends JpaRepository<IdentityUser, UUID> {
    Optional<IdentityUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
