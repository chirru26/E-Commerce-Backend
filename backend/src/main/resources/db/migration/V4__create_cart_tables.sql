CREATE TABLE carts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_carts_status CHECK (status IN ('ACTIVE', 'CHECKOUT_RESERVED', 'CHECKED_OUT', 'ABANDONED'))
);

CREATE TABLE cart_items (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    quantity BIGINT NOT NULL,
    reservation_id UUID,
    checkout_reference VARCHAR(120),
    reservation_reference VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_cart_items_cart_product UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_items_quantity_positive CHECK (quantity > 0)
);

CREATE UNIQUE INDEX uk_carts_one_active_per_user
    ON carts(user_id)
    WHERE status = 'ACTIVE';

CREATE UNIQUE INDEX uk_cart_items_reservation_id
    ON cart_items(reservation_id)
    WHERE reservation_id IS NOT NULL;

CREATE INDEX idx_carts_user_status ON carts(user_id, status);
CREATE INDEX idx_cart_items_cart_created ON cart_items(cart_id, created_at);
