package com.chirru.ecommerce.modules.cart.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items", uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_items_cart_product", columnNames = {"cart_id", "product_id"})
})
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "checkout_reference", length = 120)
    private String checkoutReference;

    @Column(name = "reservation_reference", length = 120)
    private String reservationReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartItem() {}

    public CartItem(UUID cartId, UUID productId, long quantity) {
        this.cartId = cartId;
        this.productId = productId;
        this.quantity = quantity;
    }

    public void changeQuantity(long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
        if (this.reservationId != null) {
            throw new IllegalStateException("Reserved checkout items cannot be modified");
        }
        this.quantity = quantity;
    }

    public void setCheckoutReservation(String checkoutReference, String reservationReference,
                                        UUID reservationId) {
        if (this.reservationId == null) {
            this.checkoutReference = checkoutReference;
            this.reservationReference = reservationReference;
            this.reservationId = reservationId;
        }
    }

    public void clearCheckoutReservation() {
        this.reservationId = null;
        this.checkoutReference = null;
        this.reservationReference = null;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getCartId() { return cartId; }
    public UUID getProductId() { return productId; }
    public long getQuantity() { return quantity; }
    public UUID getReservationId() { return reservationId; }
    public String getCheckoutReference() { return checkoutReference; }
    public String getReservationReference() { return reservationReference; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
