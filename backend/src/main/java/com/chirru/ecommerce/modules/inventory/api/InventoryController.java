package com.chirru.ecommerce.modules.inventory.api;

import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryAdjustmentRequest;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryCreateRequest;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryThresholdUpdateRequest;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryView;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.MovementView;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.PageResponse;
import com.chirru.ecommerce.modules.inventory.application.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@Validated
public class InventoryController {
    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public PageResponse<InventoryView> listInventory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryService.listInventory(page, size);
    }

    @GetMapping("/{productId}")
    public InventoryView getInventory(@PathVariable UUID productId) {
        return inventoryService.getInventory(productId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InventoryView initializeInventory(@Valid @RequestBody InventoryCreateRequest request) {
        return inventoryService.initializeInventory(request);
    }

    @PutMapping("/{productId}")
    public InventoryView updateThreshold(@PathVariable UUID productId,
                                         @Valid @RequestBody InventoryThresholdUpdateRequest request) {
        return inventoryService.updateThreshold(productId, request);
    }

    @PostMapping("/{productId}/adjustments")
    public InventoryView adjust(@PathVariable UUID productId,
                                @Valid @RequestBody InventoryAdjustmentRequest request) {
        return inventoryService.adjust(productId, request);
    }

    @GetMapping("/{productId}/movements")
    public PageResponse<MovementView> movements(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return inventoryService.movements(productId, page, size);
    }
}
