package com.chirru.ecommerce.modules.cart.infrastructure;

import com.chirru.ecommerce.modules.cart.application.CatalogProductPort;
import com.chirru.ecommerce.modules.catalog.domain.Product;
import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import com.chirru.ecommerce.modules.catalog.infrastructure.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component("cartCatalogProductAdapter")
public class CatalogProductAdapter implements CatalogProductPort {
    private final ProductRepository products;

    public CatalogProductAdapter(ProductRepository products) {
        this.products = products;
    }

    @Override
    public Optional<ProductSnapshot> findById(UUID productId) {
        return products.findById(productId).map(this::toSnapshot);
    }

    private ProductSnapshot toSnapshot(Product product) {
        return new ProductSnapshot(product.getId(), product.getSku(), product.getName(),
                product.getPrice(), product.getCurrency(),
                product.getStatus() == ProductStatus.ACTIVE,
                product.getCategory().isActive());
    }
}
