package com.chirru.ecommerce.modules.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_stock", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_stock_product", columnNames = "product_id")
})
public class InventoryStock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "on_hand", nullable = false)
    private long onHand;

    @Column(name = "reserved", nullable = false)
    private long reserved;

    @Column(name = "low_stock_threshold", nullable = false)
    private long lowStockThreshold;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryStock() {}

    public InventoryStock(UUID productId, long initialQuantity, long lowStockThreshold) {
        if (initialQuantity < 0) throw new IllegalArgumentException("Initial quantity cannot be negative");
        if (lowStockThreshold < 0) throw new IllegalArgumentException("Low-stock threshold cannot be negative");
        this.productId = productId;
        this.onHand = initialQuantity;
        this.reserved = 0;
        this.lowStockThreshold = lowStockThreshold;
    }

    public void adjustOnHand(long delta) {
        long next;
        try {
            next = Math.addExact(onHand, delta);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Stock quantity is outside the supported range", exception);
        }
        if (next < reserved) {
            throw new IllegalStateException("On-hand stock cannot be lower than reserved stock");
        }
        onHand = next;
    }

    public void reserve(long quantity) {
        validatePositive(quantity);
        if (availableQuantity() < quantity) {
            throw new IllegalStateException("Insufficient available stock");
        }
        try {
            reserved = Math.addExact(reserved, quantity);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Reserved quantity is outside the supported range", exception);
        }
    }

    public void release(long quantity) {
        validatePositive(quantity);
        if (reserved < quantity) {
            throw new IllegalStateException("Reserved stock is lower than the requested release");
        }
        reserved -= quantity;
    }

    public void consume(long quantity) {
        validatePositive(quantity);
        if (reserved < quantity || onHand < quantity) {
            throw new IllegalStateException("Reserved stock is insufficient for consumption");
        }
        reserved -= quantity;
        onHand -= quantity;
    }

    public void updateLowStockThreshold(long threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Low-stock threshold cannot be negative");
        this.lowStockThreshold = threshold;
    }

    public long availableQuantity() {
        return onHand - reserved;
    }

    public boolean isLowStock() {
        return availableQuantity() <= lowStockThreshold;
    }

    private static void validatePositive(long quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be greater than zero");
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
    public UUID getProductId() { return productId; }
    public long getOnHand() { return onHand; }
    public long getReserved() { return reserved; }
    public long getLowStockThreshold() { return lowStockThreshold; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
