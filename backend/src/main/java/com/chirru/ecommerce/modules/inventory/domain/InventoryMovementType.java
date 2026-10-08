package com.chirru.ecommerce.modules.inventory.domain;

public enum InventoryMovementType {
    INITIAL_STOCK,
    ADJUSTMENT_IN,
    ADJUSTMENT_OUT,
    RESERVATION,
    RELEASE,
    CONSUMPTION
}
