package com.chirru.ecommerce.modules.cart.application;

import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface CatalogProductPort {
    Optional<ProductSnapshot> findById(UUID productId);

    record ProductSnapshot(UUID id, String sku, String name, BigDecimal price,
                           String currency, ProductStatus status, boolean categoryActive) {}
}
