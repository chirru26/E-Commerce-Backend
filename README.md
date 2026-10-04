# E-Commerce Backend

Production-oriented e-commerce backend for a fresher portfolio, built as a modular monolith behind a standalone API Gateway.

## Foundation
Java 21 | Spring Boot 4.1.x | Spring Cloud Gateway | PostgreSQL | Redis | Flyway | Spring Security/JWT | OpenAPI | Docker Compose | Testcontainers | Actuator

## Maven modules
- backend — core modular-monolith application
- gateway — standalone reactive API gateway

## Topology
Client -> Gateway :8080 -> Backend :8081 -> PostgreSQL / Redis

Local tooling:
- PostgreSQL :5433
- Redis :6379
- pgAdmin :5050
- Redis Insight :5540
- Mailpit :8025

## Business modules
identity, catalog, cart, inventory, order, payment, notification

Each module will use API, application, domain and infrastructure boundaries.
