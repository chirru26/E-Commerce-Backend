package com.chirru.ecommerce.modules.cart.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CartStatus status = CartStatus.ACTIVE;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {}

    public Cart(UUID userId) {
        this.userId = userId;
    }

    public void reserveForCheckout() {
        ensureStatus(CartStatus.ACTIVE);
        status = CartStatus.CHECKOUT_RESERVED;
    }

    public void releaseCheckout() {
        if (status == CartStatus.CHECKED_OUT) {
            throw new IllegalStateException("Checked-out carts cannot be released");
        }
        if (status == CartStatus.CHECKOUT_RESERVED) {
            status = CartStatus.ACTIVE;
        }
    }

    public void completeCheckout() {
        if (status != CartStatus.CHECKOUT_RESERVED) {
            throw new IllegalStateException("Cart is not reserved for checkout");
        }
        status = CartStatus.CHECKED_OUT;
    }

    private void ensureStatus(CartStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Cart is not active");
        }
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
    public UUID getUserId() { return userId; }
    public CartStatus getStatus() { return status; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
