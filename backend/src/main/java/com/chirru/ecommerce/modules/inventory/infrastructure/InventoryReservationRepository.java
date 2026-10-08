package com.chirru.ecommerce.modules.inventory.infrastructure;

import com.chirru.ecommerce.modules.inventory.domain.InventoryReservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {
    Optional<InventoryReservation> findByReferenceKey(String referenceKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from InventoryReservation r where r.id = :id")
    Optional<InventoryReservation> findByIdForUpdate(@Param("id") UUID id);
}
