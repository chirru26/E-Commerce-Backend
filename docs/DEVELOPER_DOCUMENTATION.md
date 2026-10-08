# E-Commerce Backend Developer Documentation

## Purpose

This document explains the current architecture, module boundaries, database rules, security model, testing strategy, and conventions for extending the E-Commerce Backend.

## Architecture

```text
Client
  |
  v
Spring Cloud Gateway :8080
  |
  | /api/**
  v
Spring Boot Core :8081
  |
  +--------------------+
  |                    |
PostgreSQL            Redis
```

The core is a modular monolith. The Gateway remains separately deployable so the client entry point and backend application can scale or evolve independently.

## Module structure

Implemented modules live under:

```text
modules/<module>/
  api/
  application/
  domain/
  infrastructure/
```

### Identity

Owns users, roles, password hashing, JWT access-token issuance/validation, refresh-token lifecycle, and controlled administrator bootstrap.

### Catalog

Owns categories, products, SKUs, slugs, prices, currencies, and product status.

Product lifecycle:

```text
DRAFT -> ACTIVE -> ARCHIVED
```

Public catalog reads return only ACTIVE products in ACTIVE categories.

### Inventory

Owns on-hand stock, reserved stock, available stock, low-stock threshold, reservations, and immutable movement history.

Inventory stores `productId` and intentionally does not own Catalog entities. A small Catalog adapter provides the metadata needed at application/API boundaries.

### Cart

Owns customer carts, cart items, quantities, cart status, and checkout reservation handoff.

Cart does not own product master data or Inventory persistence.

## Cross-module boundary rules

The intended dependency direction is:

```text
Catalog -> product metadata ports/adapters
Inventory -> Catalog metadata port
Cart -> Catalog metadata port
Cart -> Inventory reservation port
Order -> Cart checkout port
Order -> Inventory application boundary
```

Domain objects should not directly reference domain entities from another module.

This keeps the modular monolith coherent and creates cleaner extraction boundaries for future services.

## Database

Flyway versions currently progress as:

```text
V1  Identity
V2  Catalog
V3  Inventory
V4  Cart
```

### Inventory invariants

```text
available = onHand - reserved
reserved <= onHand
onHand >= 0
reserved >= 0
```

Inventory writes use row locking and database constraints to enforce these invariants.

### Cart invariants

- One ACTIVE cart per user.
- One cart line per product, enforced by `(cart_id, product_id)`.
- Cart item quantity must be positive.
- Product IDs are references, not direct JPA relationships to Catalog entities.

## Security

Protected endpoint groups:

- public authentication endpoints
- public Catalog reads
- authenticated user APIs
- administrator APIs under `/api/v1/admin/**`

The JWT authentication filter validates the token and reloads the user from the database, so the current database role and enabled state participate in authorization.

## API conventions

### Versioning

```text
/api/v1/...
```

### Authentication header

```http
Authorization: Bearer <access-token>
```

### Pagination

```text
page=0
size=20
```

Maximum page size is 100.

### Soft lifecycle changes

Where historical references matter:

- product DELETE -> archive
- category DELETE -> deactivate

## Cart and Inventory checkout flow

Normal cart mutation does not reserve stock.

Checkout preparation creates a unique reservation reference for each cart item:

```text
checkout:<cart-item-id>:<attempt-id>
```

Reservation is performed through Inventory's application service and its stock row is locked before reserved quantity changes.

Lifecycle:

```text
ACTIVE
   |
   | prepareForCheckout
   v
CHECKOUT_RESERVED
   |                    |
   | failure            | success
   v                    v
releaseCheckout    completeCheckout
   |                    |
   v                    v
ACTIVE              CHECKED_OUT
```

This prevents abandoned carts from permanently holding inventory while still preventing overselling during checkout.

## Testing

Integration tests use Spring Boot, MockMvc, Testcontainers PostgreSQL, and Flyway.

Current coverage includes:

- identity registration/login/refresh/logout
- authenticated profile behavior
- role-based admin protection
- public/admin Catalog behavior
- Inventory initialization, adjustments, movement history
- Inventory reservation idempotency
- overselling prevention
- Cart CRUD
- checkout reservation/release/consume lifecycle

New modules should test both HTTP boundaries and domain/application invariants.

## Next module: Order

Recommended structure:

```text
modules/order/
  api/
  application/
  domain/
  infrastructure/
```

The Order aggregate should own:

- order identity
- userId
- order status
- immutable line-item snapshots
- totals and currency
- payment/checkout lifecycle
- timestamps and versioning

Order should not store live Catalog or Cart entities. At order creation time it should snapshot the product identity and price data required to preserve historical correctness.

Expected sequence:

```text
Cart
  -> prepare checkout
  -> reserve Inventory
  -> create Order
  -> authorize/capture Payment
  -> consume Inventory reservation
  -> complete Cart
```

Failure handling must explicitly release reservations when the order cannot proceed.

## Operations

Local service ports:

```text
Gateway        8080
Core backend   8081
PostgreSQL     5433
Redis          6379
pgAdmin        5050
Redis Insight  5540
Mailpit        8025
```

Typical startup:

```bash
docker compose up -d
mvn clean verify
```

For client traffic, use the Gateway on port 8080.

## API discovery

Springdoc exposes:

```text
Swagger UI: http://localhost:8081/swagger-ui/index.html
OpenAPI:     http://localhost:8081/v3/api-docs
```

The API Guide in `docs/API_GUIDE.md` is the behavioral reference; Swagger is the interactive explorer.

## Documentation rules for new work

1. Add or update the controller.
2. Define request/response DTOs.
3. Enforce security and ownership boundaries.
4. Add integration/domain tests.
5. Add Flyway changes for schema modifications.
6. Document request fields, response shape, status codes, and business rules.
7. Keep cross-module access behind application ports/adapters.

## Current scope

Identity, Catalog, Inventory, and Cart are implemented. Order, Payment, Notification, and later modules remain future capabilities.
