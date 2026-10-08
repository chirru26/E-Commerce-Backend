package com.chirru.ecommerce.modules.inventory.infrastructure;

import com.chirru.ecommerce.modules.catalog.infrastructure.ProductRepository;
import com.chirru.ecommerce.modules.inventory.application.CatalogProductPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("inventoryCatalogProductAdapter")
class CatalogProductAdapter implements CatalogProductPort {
    private final ProductRepository products;

    CatalogProductAdapter(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Optional<ProductSnapshot> findById(UUID productId) {
        return products.findById(productId)
                .map(product -> new ProductSnapshot(product.getId(), product.getSku(), product.getName()));
    }
}
