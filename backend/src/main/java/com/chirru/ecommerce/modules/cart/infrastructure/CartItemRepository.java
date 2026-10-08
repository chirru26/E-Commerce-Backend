package com.chirru.ecommerce.modules.cart.infrastructure;

import com.chirru.ecommerce.modules.cart.domain.CartItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    List<CartItem> findAllByCartIdOrderByCreatedAtAsc(UUID cartId);
    Optional<CartItem> findByCartIdAndProductId(UUID cartId, UUID productId);
    void deleteAllByCartId(UUID cartId);
}
