# E-Commerce Backend API Guide

## Overview

This guide documents the HTTP APIs currently implemented by the E-Commerce Backend:

- Identity and authentication
- Product Catalog
- Inventory Management
- Customer Cart

The backend uses Java 21, Spring Boot, PostgreSQL, Redis, Flyway, Spring Security/JWT, and a separate Spring Cloud Gateway.

## Base URLs

| Environment | URL | Purpose |
|---|---|---|
| Gateway | `http://localhost:8080` | Normal client entry point |
| Core backend | `http://localhost:8081` | Direct backend/local debugging |
| Swagger UI | `http://localhost:8081/swagger-ui/index.html` | Interactive API documentation |
| OpenAPI JSON | `http://localhost:8081/v3/api-docs` | Generated OpenAPI document |

Use the Gateway for normal client traffic. It forwards `/api/**` to the core backend.

## Authentication

Authenticated requests use:

```http
Authorization: Bearer <access-token>
```

Access tokens are signed JWTs and short lived. Refresh tokens are opaque random values, rotated on use, and persisted only as hashes. Public registration always creates `USER`.

## Authentication APIs

### Register

**POST** `/api/v1/auth/register`  
Auth: Public  
Response: `201 Created`

```json
{
  "email": "person@example.com",
  "password": "A-unique-password-123"
}
```

Response shape:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-token>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "userId": "<uuid>",
  "email": "person@example.com",
  "role": "USER"
}
```

Password policy: 12-72 characters and no more than 72 UTF-8 bytes.

### Login

**POST** `/api/v1/auth/login`  
Auth: Public

```json
{
  "email": "person@example.com",
  "password": "A-unique-password-123"
}
```

Returns the same token-pair shape as registration.

### Refresh

**POST** `/api/v1/auth/refresh`  
Auth: Public

```json
{
  "refreshToken": "<opaque-token>"
}
```

The refresh token is rotated. The previously used refresh token cannot be reused.

### Logout

**POST** `/api/v1/auth/logout`  
Auth: Public  
Response: `204 No Content`

```json
{
  "refreshToken": "<opaque-token>"
}
```

## Identity API

### Current user

**GET** `/api/v1/users/me`  
Auth: Bearer token

```bash
curl http://localhost:8080/api/v1/users/me \
  -H "Authorization: Bearer <access-token>"
```

Response:

```json
{
  "id": "<uuid>",
  "email": "person@example.com",
  "role": "USER",
  "createdAt": "2026-10-08T14:00:00Z"
}
```

Sensitive fields such as the password hash are not returned.

## Catalog API

Catalog owns product identity, SKU, price, currency, category, and lifecycle status. Public reads return only active categories and active products in active categories.

### Public categories

| Method | Endpoint | Auth |
|---|---|---|
| GET | `/api/v1/categories` | Public |
| GET | `/api/v1/categories/{slug}` | Public |

### Public products

| Method | Endpoint | Auth |
|---|---|---|
| GET | `/api/v1/products` | Public |
| GET | `/api/v1/products/{slug}` | Public |

Search parameters for `GET /api/v1/products`:

- `q`: optional search text, max 100 characters
- `category`: optional category slug, max 120 characters
- `page`: default 0
- `size`: default 20, maximum 100

Example:

```bash
curl "http://localhost:8080/api/v1/products?q=kumkum&category=puja-essentials&page=0&size=20"
```

Product response:

```json
{
  "id": "<uuid>",
  "name": "Kumkum Powder",
  "slug": "kumkum-powder",
  "sku": "KUM-001",
  "description": "Traditional red kumkum powder",
  "price": 149.50,
  "currency": "INR",
  "status": "ACTIVE",
  "categoryId": "<uuid>",
  "categoryName": "Puja Essentials",
  "categorySlug": "puja-essentials",
  "createdAt": "2026-10-08T14:00:00Z",
  "updatedAt": "2026-10-08T14:00:00Z"
}
```

### Administrator Catalog API

All endpoints below require `ROLE_ADMIN`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/admin/catalog/categories` | List all categories |
| POST | `/api/v1/admin/catalog/categories` | Create category |
| PUT | `/api/v1/admin/catalog/categories/{id}` | Update category |
| DELETE | `/api/v1/admin/catalog/categories/{id}` | Deactivate category |
| GET | `/api/v1/admin/catalog/products` | Search/admin-list products |
| POST | `/api/v1/admin/catalog/products` | Create product |
| PUT | `/api/v1/admin/catalog/products/{id}` | Update product |
| DELETE | `/api/v1/admin/catalog/products/{id}` | Archive product |

Category request:

```json
{
  "name": "Puja Essentials",
  "slug": "puja-essentials",
  "description": "Puja and worship products",
  "active": true
}
```

Product request:

```json
{
  "name": "Kumkum Powder",
  "slug": "kumkum-powder",
  "sku": "KUM-001",
  "description": "Traditional red kumkum powder",
  "price": 149.50,
  "currency": "INR",
  "categoryId": "<uuid>",
  "status": "ACTIVE"
}
```

Catalog rules:

- SKU and slug must be unique.
- Price must be greater than zero.
- Currency defaults to INR and uses a three-letter code.
- Products must belong to active categories.
- Deleting a product archives it.
- Deleting a category deactivates it.
- Product status defaults to `DRAFT` when omitted.

The admin product listing supports `q`, `category`, `status`, `page`, and `size`.

## Inventory API

Inventory is separate from Catalog. It owns stock state, reservations, thresholds, and the immutable movement ledger. All Inventory HTTP endpoints require `ROLE_ADMIN`.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/v1/admin/inventory?page=0&size=20` | List inventory |
| GET | `/api/v1/admin/inventory/{productId}` | Inspect one stock record |
| POST | `/api/v1/admin/inventory` | Initialize stock |
| PUT | `/api/v1/admin/inventory/{productId}` | Update low-stock threshold |
| POST | `/api/v1/admin/inventory/{productId}/adjustments` | Apply stock adjustment |
| GET | `/api/v1/admin/inventory/{productId}/movements?page=0&size=20` | View movement history |

### Initialize inventory

**POST** `/api/v1/admin/inventory`

```json
{
  "productId": "<uuid>",
  "initialQuantity": 100,
  "lowStockThreshold": 10
}
```

### Update low-stock threshold

**PUT** `/api/v1/admin/inventory/{productId}`

```json
{
  "lowStockThreshold": 10
}
```

### Stock adjustment

**POST** `/api/v1/admin/inventory/{productId}/adjustments`

```json
{
  "quantityDelta": -3,
  "reason": "Damaged units removed"
}
```

A positive delta adds stock; a negative delta removes stock. Delta cannot be zero.

### Inventory response

```json
{
  "productId": "<uuid>",
  "sku": "KUM-001",
  "productName": "Kumkum Powder",
  "onHand": 100,
  "reserved": 15,
  "available": 85,
  "lowStockThreshold": 10,
  "lowStock": false,
  "createdAt": "2026-10-08T14:00:00Z",
  "updatedAt": "2026-10-08T14:05:00Z"
}
```

### Inventory invariants

```text
available = onHand - reserved
reserved <= onHand
```

Reservation and adjustment writes lock the stock row. The movement ledger records:

```text
INITIAL_STOCK
ADJUSTMENT_IN
ADJUSTMENT_OUT
RESERVATION
RELEASE
CONSUMPTION
```

## Cart API

Cart belongs to the authenticated customer. It stores product IDs and quantities; Catalog remains the source for product metadata and current price.

| Method | Endpoint | Auth |
|---|---|---|
| GET | `/api/v1/cart` | Bearer |
| POST | `/api/v1/cart/items` | Bearer |
| PUT | `/api/v1/cart/items/{productId}` | Bearer |
| DELETE | `/api/v1/cart/items/{productId}` | Bearer |
| DELETE | `/api/v1/cart` | Bearer |

### Get cart

**GET** `/api/v1/cart`

An active empty cart is created when the user has no checkout-reserved cart.

### Add item

**POST** `/api/v1/cart/items`

```json
{
  "productId": "<uuid>",
  "quantity": 2
}
```

### Update item

**PUT** `/api/v1/cart/items/{productId}`

```json
{
  "quantity": 4
}
```

### Remove item

**DELETE** `/api/v1/cart/items/{productId}`

Response: `204 No Content`.

### Clear cart

**DELETE** `/api/v1/cart`

Response: `204 No Content`.

### Cart rules

- Quantity is 1..1000 per product.
- Maximum 100 distinct products per cart.
- Only ACTIVE products in ACTIVE categories can be added or updated.
- A cart cannot mix currencies.
- Cart totals use the current Catalog price at read time.
- Normal cart contents do not reserve Inventory.

Cart response:

```json
{
  "id": "<uuid>",
  "status": "ACTIVE",
  "items": [
    {
      "productId": "<uuid>",
      "sku": "INC-001",
      "name": "Incense Sticks",
      "unitPrice": 99.00,
      "currency": "INR",
      "quantity": 2,
      "lineTotal": 198.00,
      "productActive": true
    }
  ],
  "itemCount": 2,
  "subtotal": 198.00,
  "currency": "INR",
  "createdAt": "2026-10-08T14:00:00Z",
  "updatedAt": "2026-10-08T14:03:00Z"
}
```

## Checkout Reservation Contract

The checkout orchestration is currently an internal application contract for the future Order module. It is not a customer-facing HTTP API.

### Prepare checkout

```text
CartService.prepareForCheckout(userId, checkoutReference)
```

Flow:

```text
ACTIVE
  -> validate Catalog items
  -> Inventory.reserve(...)
  -> attach reservation IDs
  -> CHECKOUT_RESERVED
```

The same checkout reference can be retried for an already-reserved cart.

### Release checkout

```text
CartService.releaseCheckout(userId, checkoutReference)
```

Flow:

```text
CHECKOUT_RESERVED
  -> Inventory.release(...)
  -> ACTIVE
```

Use this when order creation or payment fails.

### Complete checkout

```text
CartService.completeCheckout(userId, checkoutReference)
```

Flow:

```text
CHECKOUT_RESERVED
  -> Inventory.consume(...)
  -> CHECKED_OUT
```

The next customer `GET /api/v1/cart` creates/returns a new active cart.

## Error Handling

| Status | Meaning | Typical examples |
|---|---|---|
| 200 | Successful read/update | GET product, update cart, inventory adjustment |
| 201 | Resource created | Register user, create category/product, initialize inventory |
| 204 | No response body | Logout, remove/clear cart, deactivate/archive |
| 400 | Validation/input problem | Invalid quantity, page, email, request body |
| 401 | Missing/invalid authentication | Missing or expired bearer token |
| 403 | Authenticated but insufficient role | USER calling admin endpoint |
| 404 | Resource not found | Unknown product/inventory |
| 409 | Business or uniqueness conflict | Duplicate SKU, insufficient stock, checkout-state conflict |

There is not yet one universal JSON error envelope across every endpoint; clients should use the HTTP status and current response body.

## End-to-end flow

```text
Register/Login
    |
    v
Browse Catalog
GET /api/v1/products
    |
    v
Add Cart Items
POST /api/v1/cart/items
    |
    v
Read Cart
GET /api/v1/cart
    |
    v
Future Order module prepares checkout
    |
    v
Inventory reservations protect stock
    |
    +---- failure ----> release
    |
    +---- success ----> consume
```

## Developer Notes

- Use the Gateway URL in frontend/client integrations.
- Keep access tokens out of URLs and query parameters.
- Send `Content-Type: application/json` for JSON request bodies.
- Use Swagger/OpenAPI for interactive API discovery.
- Order should snapshot price data rather than treating the live Cart price as an immutable order price.
- Cross-module inventory and cart operations should use application ports/services, not direct persistence access.
- Order should use `CartCheckoutPort`, not Cart tables.

## Current Scope

Implemented customer/admin APIs: Identity, Catalog, Inventory, and Cart.

Not yet implemented as customer-facing APIs: Order, Payment, Notification, and later modules.
