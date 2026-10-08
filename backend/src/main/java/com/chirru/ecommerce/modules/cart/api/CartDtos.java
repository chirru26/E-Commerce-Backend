package com.chirru.ecommerce.modules.cart.api;

import com.chirru.ecommerce.modules.cart.domain.CartStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CartDtos {
    private CartDtos() {}

    public record AddCartItemRequest(
            @NotNull UUID productId,
            @Min(1) @Max(1000) int quantity) {}

    public record UpdateCartItemRequest(
            @Min(1) @Max(1000) int quantity) {}

    public record CartItemView(
            UUID productId,
            String sku,
            String name,
            BigDecimal unitPrice,
            String currency,
            long quantity,
            BigDecimal lineTotal,
            boolean productActive) {}

    public record CartView(
            UUID id,
            CartStatus status,
            List<CartItemView> items,
            long itemCount,
            BigDecimal subtotal,
            String currency,
            Instant createdAt,
            Instant updatedAt) {}
}
