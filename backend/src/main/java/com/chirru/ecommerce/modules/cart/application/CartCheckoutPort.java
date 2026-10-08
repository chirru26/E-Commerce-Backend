package com.chirru.ecommerce.modules.cart.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface CartCheckoutPort {
    CheckoutSnapshot prepareForCheckout(UUID userId, String checkoutReference);
    void releaseCheckout(UUID userId, String checkoutReference);
    void completeCheckout(UUID userId, String checkoutReference);

    record CheckoutSnapshot(UUID cartId, UUID userId, String checkoutReference,
                            List<Item> items) {
        public record Item(UUID productId, long quantity, UUID reservationId,
                           String sku, String name, BigDecimal unitPrice, String currency) {}
    }
}
