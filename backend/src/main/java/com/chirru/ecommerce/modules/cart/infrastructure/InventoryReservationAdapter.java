package com.chirru.ecommerce.modules.cart.infrastructure;

import com.chirru.ecommerce.modules.cart.application.InventoryReservationPort;
import com.chirru.ecommerce.modules.inventory.application.InventoryService;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationAdapter implements InventoryReservationPort {
    private final InventoryService inventoryService;

    public InventoryReservationAdapter(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public UUID reserve(UUID productId, long quantity, String referenceKey) {
        return inventoryService.reserve(productId, quantity, referenceKey).id();
    }

    @Override
    public void release(UUID reservationId) {
        inventoryService.releaseReservation(reservationId);
    }

    @Override
    public void consume(UUID reservationId) {
        inventoryService.consumeReservation(reservationId);
    }
}
