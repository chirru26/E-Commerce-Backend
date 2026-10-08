package com.chirru.ecommerce.modules.inventory.infrastructure;

import com.chirru.ecommerce.modules.inventory.domain.InventoryMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    Page<InventoryMovement> findByProductIdOrderByCreatedAtDesc(UUID productId, Pageable pageable);
}
