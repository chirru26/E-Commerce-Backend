package com.chirru.ecommerce.modules.inventory.application;

import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryAdjustmentRequest;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryCreateRequest;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryView;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.MovementView;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.PageResponse;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.ReservationView;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryThresholdUpdateRequest;
import com.chirru.ecommerce.modules.inventory.domain.InventoryMovement;
import com.chirru.ecommerce.modules.inventory.domain.InventoryMovementType;
import com.chirru.ecommerce.modules.inventory.domain.InventoryReservation;
import com.chirru.ecommerce.modules.inventory.domain.InventoryReservationStatus;
import com.chirru.ecommerce.modules.inventory.domain.InventoryStock;
import com.chirru.ecommerce.modules.inventory.infrastructure.InventoryMovementRepository;
import com.chirru.ecommerce.modules.inventory.infrastructure.InventoryReservationRepository;
import com.chirru.ecommerce.modules.inventory.infrastructure.InventoryStockRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class InventoryService {
    private static final int MAX_PAGE_SIZE = 100;

    private final InventoryStockRepository stocks;
    private final InventoryReservationRepository reservations;
    private final InventoryMovementRepository movements;
    private final CatalogProductPort catalogProducts;

    public InventoryService(InventoryStockRepository stocks,
                            InventoryReservationRepository reservations,
                            InventoryMovementRepository movements,
                            CatalogProductPort catalogProducts) {
        this.stocks = stocks;
        this.reservations = reservations;
        this.movements = movements;
        this.catalogProducts = catalogProducts;
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryView> listInventory(int page, int size) {
        return PageResponse.from(stocks.findAllByOrderByUpdatedAtDesc(pageRequest(page, size))
                .map(this::toView));
    }

    @Transactional(readOnly = true)
    public InventoryView getInventory(UUID productId) {
        return toView(stocks.findByProductId(productId)
                .orElseThrow(() -> notFound("Inventory record not found")));
    }

    public InventoryView initializeInventory(InventoryCreateRequest request) {
        requireProduct(request.productId());
        if (stocks.existsByProductId(request.productId())) {
            throw conflict("Inventory record already exists for this product");
        }

        InventoryStock stock = new InventoryStock(request.productId(),
                request.initialQuantity(), request.lowStockThreshold());
        stocks.save(stock);

        if (request.initialQuantity() > 0) {
            recordMovement(stock.getProductId(), InventoryMovementType.INITIAL_STOCK,
                    request.initialQuantity(), 0, null, null, "Initial inventory");
        }
        return toView(stock);
    }

    public InventoryView updateThreshold(UUID productId, InventoryThresholdUpdateRequest request) {
        InventoryStock stock = lockStock(productId);
        try {
            stock.updateLowStockThreshold(request.lowStockThreshold());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        }
        return toView(stock);
    }

    public InventoryView adjust(UUID productId, InventoryAdjustmentRequest request) {
        if (request.quantityDelta() == 0) {
            throw badRequest("Quantity delta must not be zero");
        }
        InventoryStock stock = lockStock(productId);
        InventoryMovementType type = request.quantityDelta() > 0
                ? InventoryMovementType.ADJUSTMENT_IN
                : InventoryMovementType.ADJUSTMENT_OUT;
        try {
            stock.adjustOnHand(request.quantityDelta());
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        recordMovement(stock.getProductId(), type, request.quantityDelta(), 0,
                null, null, request.reason().trim());
        return toView(stock);
    }

    public ReservationView reserve(UUID productId, long quantity, String referenceKey) {
        validatePositiveQuantity(quantity);
        String key = normalizeReferenceKey(referenceKey);

        var existing = reservations.findByReferenceKey(key);
        if (existing.isPresent()) {
            InventoryReservation reservation = existing.get();
            if (!reservation.getProductId().equals(productId) || reservation.getQuantity() != quantity) {
                throw conflict("Reservation reference key is already used with different data");
            }
            if (reservation.getStatus() != InventoryReservationStatus.ACTIVE) {
                throw conflict("Reservation reference key has already been finalized");
            }
            return toReservationView(reservation);
        }

        requireProduct(productId);
        InventoryStock stock = lockStock(productId);
        try {
            stock.reserve(quantity);
        } catch (IllegalArgumentException exception) {
            throw badRequest(exception.getMessage());
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }

        InventoryReservation reservation = reservations.save(
                new InventoryReservation(productId, quantity, key));
        recordMovement(productId, InventoryMovementType.RESERVATION, 0, quantity,
                reservation.getId(), key, "Stock reserved");
        return toReservationView(reservation);
    }

    public ReservationView releaseReservation(UUID reservationId) {
        InventoryReservation reservation = lockReservation(reservationId);
        if (reservation.getStatus() != InventoryReservationStatus.ACTIVE) {
            return toReservationView(reservation);
        }

        InventoryStock stock = lockStock(reservation.getProductId());
        try {
            stock.release(reservation.getQuantity());
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        reservation.release();
        recordMovement(reservation.getProductId(), InventoryMovementType.RELEASE,
                0, -reservation.getQuantity(), reservation.getId(),
                reservation.getReferenceKey(), "Reservation released");
        return toReservationView(reservation);
    }

    public ReservationView consumeReservation(UUID reservationId) {
        InventoryReservation reservation = lockReservation(reservationId);
        if (reservation.getStatus() == InventoryReservationStatus.CONSUMED) {
            return toReservationView(reservation);
        }
        if (reservation.getStatus() == InventoryReservationStatus.RELEASED) {
            throw conflict("Released reservations cannot be consumed");
        }

        InventoryStock stock = lockStock(reservation.getProductId());
        try {
            stock.consume(reservation.getQuantity());
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }
        reservation.consume();
        recordMovement(reservation.getProductId(), InventoryMovementType.CONSUMPTION,
                -reservation.getQuantity(), -reservation.getQuantity(),
                reservation.getId(), reservation.getReferenceKey(), "Reservation consumed");
        return toReservationView(reservation);
    }

    @Transactional(readOnly = true)
    public PageResponse<MovementView> movements(UUID productId, int page, int size) {
        if (stocks.findByProductId(productId).isEmpty()) {
            throw notFound("Inventory record not found");
        }
        return PageResponse.from(movements.findByProductIdOrderByCreatedAtDesc(
                        productId, movementPageRequest(page, size))
                .map(InventoryService::toMovementView));
    }

    private InventoryStock lockStock(UUID productId) {
        return stocks.findByProductIdForUpdate(productId)
                .orElseThrow(() -> notFound("Inventory record not found"));
    }

    private InventoryReservation lockReservation(UUID id) {
        return reservations.findByIdForUpdate(id)
                .orElseThrow(() -> notFound("Reservation not found"));
    }

    private void requireProduct(UUID productId) {
        if (catalogProducts.findById(productId).isEmpty()) {
            throw notFound("Product not found");
        }
    }

    private InventoryView toView(InventoryStock stock) {
        CatalogProductPort.ProductSnapshot product =
                catalogProducts.findById(stock.getProductId()).orElse(null);
        return new InventoryView(
                stock.getProductId(),
                product == null ? null : product.sku(),
                product == null ? null : product.name(),
                stock.getOnHand(),
                stock.getReserved(),
                stock.availableQuantity(),
                stock.getLowStockThreshold(),
                stock.isLowStock(),
                stock.getCreatedAt(),
                stock.getUpdatedAt());
    }

    private static ReservationView toReservationView(InventoryReservation reservation) {
        return new ReservationView(reservation.getId(), reservation.getProductId(),
                reservation.getQuantity(), reservation.getReferenceKey(),
                reservation.getStatus(), reservation.getCreatedAt(), reservation.getUpdatedAt());
    }

    private static MovementView toMovementView(InventoryMovement movement) {
        return new MovementView(movement.getId(), movement.getProductId(), movement.getType(),
                movement.getOnHandDelta(), movement.getReservedDelta(),
                movement.getReservationId(), movement.getReferenceKey(),
                movement.getReason(), movement.getCreatedAt());
    }

    private void recordMovement(UUID productId, InventoryMovementType type,
                                long onHandDelta, long reservedDelta,
                                UUID reservationId, String referenceKey, String reason) {
        movements.save(new InventoryMovement(productId, type, onHandDelta, reservedDelta,
                reservationId, referenceKey, reason));
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0) throw badRequest("Page cannot be negative");
        if (size < 1) throw badRequest("Page size must be at least 1");
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "updatedAt"));
    }

    private static PageRequest movementPageRequest(int page, int size) {
        if (page < 0) throw badRequest("Page cannot be negative");
        if (size < 1) throw badRequest("Page size must be at least 1");
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static void validatePositiveQuantity(long quantity) {
        if (quantity <= 0) throw badRequest("Quantity must be greater than zero");
    }

    private static String normalizeReferenceKey(String value) {
        if (value == null || value.isBlank()) throw badRequest("Reservation reference key is required");
        String normalized = value.trim();
        if (normalized.length() > 120) throw badRequest("Reservation reference key must be 120 characters or fewer");
        return normalized;
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
