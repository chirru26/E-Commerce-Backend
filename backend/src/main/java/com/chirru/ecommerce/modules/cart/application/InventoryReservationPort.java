package com.chirru.ecommerce.modules.cart.application;

import java.util.UUID;

public interface InventoryReservationPort {
    UUID reserve(UUID productId, long quantity, String referenceKey);
    void release(UUID reservationId);
    void consume(UUID reservationId);
}
