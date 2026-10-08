package com.chirru.ecommerce.modules.cart.infrastructure;

import com.chirru.ecommerce.modules.cart.domain.Cart;
import com.chirru.ecommerce.modules.cart.domain.CartStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cart c where c.userId = :userId and c.status = :status")
    Optional<Cart> findByUserIdAndStatusForUpdate(@Param("userId") UUID userId,
                                                  @Param("status") CartStatus status);

    Optional<Cart> findByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO carts (id, user_id, status, version, created_at, updated_at)
            VALUES (:id, :userId, 'ACTIVE', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int createActiveIfAbsent(@Param("id") UUID id, @Param("userId") UUID userId);
}
