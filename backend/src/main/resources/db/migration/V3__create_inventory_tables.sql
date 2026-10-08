CREATE TABLE inventory_stock (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    on_hand BIGINT NOT NULL DEFAULT 0,
    reserved BIGINT NOT NULL DEFAULT 0,
    low_stock_threshold BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_stock_product UNIQUE (product_id),
    CONSTRAINT ck_inventory_stock_on_hand_non_negative CHECK (on_hand >= 0),
    CONSTRAINT ck_inventory_stock_reserved_non_negative CHECK (reserved >= 0),
    CONSTRAINT ck_inventory_stock_reserved_not_above_on_hand CHECK (reserved <= on_hand),
    CONSTRAINT ck_inventory_stock_low_threshold_non_negative CHECK (low_stock_threshold >= 0)
);

CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    reference_key VARCHAR(120) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inventory_reservations_reference_key UNIQUE (reference_key),
    CONSTRAINT ck_inventory_reservations_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservations_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED'))
);

CREATE TABLE inventory_movements (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    on_hand_delta BIGINT NOT NULL DEFAULT 0,
    reserved_delta BIGINT NOT NULL DEFAULT 0,
    reservation_id UUID,
    reference_key VARCHAR(120),
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_inventory_movements_type CHECK (
        type IN ('INITIAL_STOCK', 'ADJUSTMENT_IN', 'ADJUSTMENT_OUT',
                 'RESERVATION', 'RELEASE', 'CONSUMPTION')
    ),
    CONSTRAINT ck_inventory_movements_not_empty CHECK (on_hand_delta <> 0 OR reserved_delta <> 0)
);

CREATE INDEX idx_inventory_stock_updated ON inventory_stock(updated_at DESC);
CREATE INDEX idx_inventory_reservations_product_status ON inventory_reservations(product_id, status);
CREATE INDEX idx_inventory_movements_product_created ON inventory_movements(product_id, created_at DESC);
CREATE INDEX idx_inventory_movements_reservation ON inventory_movements(reservation_id);
