CREATE TABLE catalog_categories (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    slug VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_catalog_categories_slug UNIQUE (slug)
);

CREATE TABLE catalog_products (
    id UUID PRIMARY KEY,
    name VARCHAR(180) NOT NULL,
    slug VARCHAR(200) NOT NULL,
    sku VARCHAR(64) NOT NULL,
    description VARCHAR(4000),
    price NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'INR',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    category_id UUID NOT NULL REFERENCES catalog_categories(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_catalog_products_slug UNIQUE (slug),
    CONSTRAINT uk_catalog_products_sku UNIQUE (sku),
    CONSTRAINT ck_catalog_products_price_positive CHECK (price > 0),
    CONSTRAINT ck_catalog_products_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_catalog_products_category ON catalog_products(category_id);
CREATE INDEX idx_catalog_products_status_created ON catalog_products(status, created_at DESC);
