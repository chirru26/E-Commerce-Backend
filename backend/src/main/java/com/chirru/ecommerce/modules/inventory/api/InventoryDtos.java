package com.chirru.ecommerce.modules.inventory.api;

import com.chirru.ecommerce.modules.inventory.domain.InventoryMovementType;
import com.chirru.ecommerce.modules.inventory.domain.InventoryReservationStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record InventoryCreateRequest(
            @NotNull UUID productId,
            @Min(0) long initialQuantity,
            @Min(0) long lowStockThreshold) {}

    public record InventoryThresholdUpdateRequest(
            @NotNull @Min(0) Long lowStockThreshold) {}

    public record InventoryAdjustmentRequest(
            @NotNull Long quantityDelta,
            @NotBlank @Size(max = 500) String reason) {}

    public record InventoryView(
            UUID productId,
            String sku,
            String productName,
            long onHand,
            long reserved,
            long available,
            long lowStockThreshold,
            boolean lowStock,
            Instant createdAt,
            Instant updatedAt) {}

    public record ReservationView(
            UUID id,
            UUID productId,
            long quantity,
            String referenceKey,
            InventoryReservationStatus status,
            Instant createdAt,
            Instant updatedAt) {}

    public record MovementView(
            UUID id,
            UUID productId,
            InventoryMovementType type,
            long onHandDelta,
            long reservedDelta,
            UUID reservationId,
            String referenceKey,
            String reason,
            Instant createdAt) {}

    public record PageResponse<T>(
            List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }
}
