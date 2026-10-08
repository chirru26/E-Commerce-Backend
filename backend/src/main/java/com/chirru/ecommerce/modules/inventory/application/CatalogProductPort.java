package com.chirru.ecommerce.modules.inventory.application;

import java.util.Optional;
import java.util.UUID;

public interface CatalogProductPort {
    Optional<ProductSnapshot> findById(UUID productId);

    record ProductSnapshot(UUID id, String sku, String name) {}
}
