package com.chirru.ecommerce.modules.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryMovementType type;

    @Column(name = "on_hand_delta", nullable = false)
    private long onHandDelta;

    @Column(name = "reserved_delta", nullable = false)
    private long reservedDelta;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "reference_key", length = 120)
    private String referenceKey;

    @Column(length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InventoryMovement() {}

    public InventoryMovement(UUID productId, InventoryMovementType type,
                             long onHandDelta, long reservedDelta,
                             UUID reservationId, String referenceKey, String reason) {
        this.productId = productId;
        this.type = type;
        this.onHandDelta = onHandDelta;
        this.reservedDelta = reservedDelta;
        this.reservationId = reservationId;
        this.referenceKey = referenceKey;
        this.reason = reason;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public InventoryMovementType getType() { return type; }
    public long getOnHandDelta() { return onHandDelta; }
    public long getReservedDelta() { return reservedDelta; }
    public UUID getReservationId() { return reservationId; }
    public String getReferenceKey() { return referenceKey; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
