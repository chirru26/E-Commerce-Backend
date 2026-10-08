package com.chirru.ecommerce.modules.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservations", uniqueConstraints = {
        @UniqueConstraint(name = "uk_inventory_reservations_reference_key", columnNames = "reference_key")
})
public class InventoryReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long quantity;

    @Column(name = "reference_key", nullable = false, length = 120)
    private String referenceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryReservationStatus status = InventoryReservationStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {}

    public InventoryReservation(UUID productId, long quantity, String referenceKey) {
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be greater than zero");
        this.productId = productId;
        this.quantity = quantity;
        this.referenceKey = referenceKey;
    }

    public void release() {
        if (status == InventoryReservationStatus.ACTIVE) status = InventoryReservationStatus.RELEASED;
    }

    public void consume() {
        if (status == InventoryReservationStatus.ACTIVE) status = InventoryReservationStatus.CONSUMED;
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
    public long getQuantity() { return quantity; }
    public String getReferenceKey() { return referenceKey; }
    public InventoryReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
