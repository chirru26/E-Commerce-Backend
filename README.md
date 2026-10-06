# E-Commerce Backend

Production-oriented e-commerce backend built with Java 21 and Spring Boot. The core is a modular monolith behind a separately deployable Spring Cloud Gateway.

## Architecture

`Client -> Gateway :8080 -> Core backend :8081 -> PostgreSQL / Redis`

Core modules: Identity, Catalog, Cart, Inventory, Order, Payment, Notification. Each module uses API, application, domain and infrastructure boundaries.

## Requirements

- JDK 21
- Maven 3.9+ (or a local Maven installation)
- Docker Desktop / Docker Engine with Compose v2

## Local setup

1. Copy `.env.example` to `.env`.
2. Replace all `change-me` values and set `JWT_SECRET` to a private random value of at least 32 UTF-8 bytes. Never commit `.env`.
3. Start infrastructure: `docker compose up -d postgres redis pgadmin redisinsight mailpit`.
4. Build and test all modules: `mvn clean verify`.
5. Run the backend from the repository root: `mvn -pl backend spring-boot:run`.
6. In another terminal, run the gateway: `mvn -pl gateway spring-boot:run`.

The gateway listens on port 8080 and forwards `/api/**` to the core backend on port 8081. PostgreSQL is published on port 5433, Redis on 6379, pgAdmin on 5050, Redis Insight on 5540, and Mailpit's UI on 8025.

## Docker Compose

With `.env` configured, run `docker compose up --build -d`. Both app images build from source using multi-stage Maven builds. Check startup with `docker compose ps` and `docker compose logs -f backend gateway`.

## Identity API

- `POST /api/v1/auth/register` — create a regular user and return tokens.
- `POST /api/v1/auth/login` — authenticate.
- `POST /api/v1/auth/refresh` — rotate a refresh token.
- `POST /api/v1/auth/logout` — revoke a refresh token.
- `GET /api/v1/users/me` — return the authenticated user's safe profile; requires a Bearer access token.

Example registration/login JSON:

```json
{
  "email": "person@example.com",
  "password": "use-a-long-unique-password"
}
```

Public registration always creates a `USER`; it does not accept a role field. Access tokens are short-lived signed JWTs. The authentication filter verifies the token, reloads the account, and uses the current database role and enabled status for authorization. Refresh tokens are random opaque values; only SHA-256 hashes are persisted, and refresh tokens rotate on use.

## Controlled administrator bootstrap

Admin provisioning is disabled by default. To create the first administrator intentionally:

1. Set `ADMIN_BOOTSTRAP_ENABLED=true`, `ADMIN_BOOTSTRAP_EMAIL`, and `ADMIN_BOOTSTRAP_PASSWORD` in your untracked `.env`.
2. Use a unique password between 12 and 72 characters and no more than 72 UTF-8 bytes.
3. Start/restart the backend once so the bootstrap runner can create the administrator if that email does not exist.
4. Immediately set `ADMIN_BOOTSTRAP_ENABLED=false` and remove the bootstrap password from `.env`, then restart the backend.

If the email already belongs to an enabled administrator, bootstrap is a no-op. If it belongs to a normal or disabled account, application startup fails rather than promoting that account. Do not leave bootstrap enabled or use it as a routine production provisioning mechanism. Use HTTPS and keep all secrets out of source control.

## Product Catalog API

Public reads do not require authentication:

- `GET /api/v1/categories` and `GET /api/v1/categories/{slug}` — active categories.
- `GET /api/v1/products?q=kumkum&category=puja-essentials&page=0&size=20` — search active products with pagination.
- `GET /api/v1/products/{slug}` — public product details.

Catalog writes and management are restricted to administrators:

- `GET|POST /api/v1/admin/catalog/categories`
- `PUT|DELETE /api/v1/admin/catalog/categories/{id}`
- `GET|POST /api/v1/admin/catalog/products`
- `PUT|DELETE /api/v1/admin/catalog/products/{id}`

Product creation requires a unique SKU, price greater than zero, currency code (defaults to INR), and an active category. Products default to `DRAFT` unless an administrator explicitly sets their status to `ACTIVE`. Public search only returns active products in active categories. Deleting a product archives it, and deleting a category deactivates it, preserving references for future orders and inventory records. Public pagination defaults to 20 products and caps page size at 100.

## Verification

GitHub Actions runs `mvn clean verify`, validates the Compose configuration, and builds both application images for pushes to `main` and pull requests. See the repository's Actions tab for the latest result.
