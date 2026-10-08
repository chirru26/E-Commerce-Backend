package com.chirru.ecommerce.modules.inventory.infrastructure;

import com.chirru.ecommerce.modules.inventory.domain.InventoryStock;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InventoryStockRepository extends JpaRepository<InventoryStock, UUID> {
    boolean existsByProductId(UUID productId);

    Optional<InventoryStock> findByProductId(UUID productId);

    Page<InventoryStock> findAllByOrderByUpdatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventoryStock s where s.productId = :productId")
    Optional<InventoryStock> findByProductIdForUpdate(@Param("productId") UUID productId);
}
