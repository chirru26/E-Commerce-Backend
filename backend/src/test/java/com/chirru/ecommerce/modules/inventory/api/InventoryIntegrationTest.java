package com.chirru.ecommerce.modules.inventory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chirru.ecommerce.modules.catalog.domain.Category;
import com.chirru.ecommerce.modules.catalog.domain.Product;
import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import com.chirru.ecommerce.modules.catalog.infrastructure.CategoryRepository;
import com.chirru.ecommerce.modules.catalog.infrastructure.ProductRepository;
import com.chirru.ecommerce.modules.identity.application.AuthService;
import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import com.chirru.ecommerce.modules.identity.infrastructure.RefreshTokenRepository;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.ReservationView;
import com.chirru.ecommerce.modules.inventory.application.InventoryService;
import com.chirru.ecommerce.modules.inventory.domain.InventoryReservationStatus;
import com.chirru.ecommerce.modules.inventory.infrastructure.InventoryMovementRepository;
import com.chirru.ecommerce.modules.inventory.infrastructure.InventoryReservationRepository;
import com.chirru.ecommerce.modules.inventory.infrastructure.InventoryStockRepository;
import com.chirru.ecommerce.security.JwtService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class InventoryIntegrationTest {
    private static final String JWT_TEST_SECRET =
            "inventory-integration-tests-use-a-secret-at-least-32-bytes-long";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("inventory_test")
                    .withUsername("inventory_test")
                    .withPassword("inventory_test");

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.security.jwt.secret", () -> JWT_TEST_SECRET);
        registry.add("app.security.jwt.access-token-expiration", () -> 900);
        registry.add("app.security.jwt.refresh-token-expiration", () -> 604800);
    }

    @Autowired MockMvc mockMvc;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired InventoryStockRepository stocks;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryMovementRepository movements;
    @Autowired IdentityUserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired InventoryService inventoryService;

    @BeforeEach
    void cleanDatabase() {
        movements.deleteAll();
        reservations.deleteAll();
        stocks.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void anonymousAndRegularUsersCannotManageInventory() throws Exception {
        mockMvc.perform(get("/api/v1/admin/inventory"))
                .andExpect(status().isUnauthorized());

        String userToken = authService.register(
                "inventory-user@example.com", "A-unique-password-123").accessToken();

        mockMvc.perform(get("/api/v1/admin/inventory")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanInitializeAdjustAndInspectInventory() throws Exception {
        Product product = createProduct("Incense Sticks", "INC-001");
        String adminToken = createAdminToken();

        String initJson = """
                {"productId":"%s","initialQuantity":10,"lowStockThreshold":2}
                """.formatted(product.getId());

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(initJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("INC-001"))
                .andExpect(jsonPath("$.onHand").value(10))
                .andExpect(jsonPath("$.reserved").value(0))
                .andExpect(jsonPath("$.available").value(10))
                .andExpect(jsonPath("$.lowStock").value(false));

        mockMvc.perform(post("/api/v1/admin/inventory/" + product.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":5,"reason":"Received supplier shipment"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(15));

        mockMvc.perform(post("/api/v1/admin/inventory/" + product.getId() + "/adjustments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantityDelta":-3,"reason":"Damaged units removed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onHand").value(12));

        mockMvc.perform(get("/api/v1/admin/inventory/" + product.getId() + "/movements")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void reservationsPreventOversellingAndAreIdempotentWhileActive() {
        Product product = createProduct("Camphor", "CAM-001");
        inventoryService.initializeInventory(
                new com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryCreateRequest(
                        product.getId(), 10, 1));

        ReservationView first = inventoryService.reserve(product.getId(), 7, "cart-100");
        ReservationView repeat = inventoryService.reserve(product.getId(), 7, "cart-100");

        assertEquals(first.id(), repeat.id());
        assertEquals(InventoryReservationStatus.ACTIVE, repeat.status());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> inventoryService.reserve(product.getId(), 4, "cart-101"));
        assertEquals(409, exception.getStatusCode().value());

        ReservationView released = inventoryService.releaseReservation(first.id());
        assertEquals(InventoryReservationStatus.RELEASED, released.status());
        assertEquals(10, stocks.findByProductId(product.getId()).orElseThrow().availableQuantity());

        ReservationView second = inventoryService.reserve(product.getId(), 6, "cart-102");
        ReservationView consumed = inventoryService.consumeReservation(second.id());
        assertEquals(InventoryReservationStatus.CONSUMED, consumed.status());

        var stock = stocks.findByProductId(product.getId()).orElseThrow();
        assertEquals(4, stock.getOnHand());
        assertEquals(0, stock.getReserved());
        assertEquals(4, stock.availableQuantity());
    }

    @Test
    void adjustmentCannotCreateAvailableStockBelowReservedQuantity() {
        Product product = createProduct("Kumkum", "KUM-001");
        inventoryService.initializeInventory(
                new com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryCreateRequest(
                        product.getId(), 5, 0));
        var reservation = inventoryService.reserve(product.getId(), 3, "cart-200");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> inventoryService.adjust(product.getId(),
                        new com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryAdjustmentRequest(
                                -3L, "Remove stock")));

        assertEquals(409, exception.getStatusCode().value());
        var stock = stocks.findByProductId(product.getId()).orElseThrow();
        assertEquals(5, stock.getOnHand());
        assertEquals(3, stock.getReserved());

        inventoryService.releaseReservation(reservation.id());
    }

    @Test
    void rejectsUnknownProductsAndDuplicateInventoryRecords() throws Exception {
        String adminToken = createAdminToken();
        UUID missingProduct = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","initialQuantity":5,"lowStockThreshold":0}
                                """.formatted(missingProduct)))
                .andExpect(status().isNotFound());

        Product product = createProduct("Ghee Lamp", "GHEE-001");
        String request = """
                {"productId":"%s","initialQuantity":5,"lowStockThreshold":0}
                """.formatted(product.getId());

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/inventory")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/admin/inventory/" + product.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"lowStockThreshold":2}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStockThreshold").value(2));
    }

    private Product createProduct(String name, String sku) {
        Category category = categories.saveAndFlush(
                new Category("Puja Essentials", "puja-essentials", "Puja products", true));
        return products.saveAndFlush(new Product(
                name, name.toLowerCase().replace(' ', '-'), sku, "Inventory test product",
                new BigDecimal("99.00"), "INR", ProductStatus.ACTIVE, category));
    }

    private String createAdminToken() {
        IdentityUser admin = users.saveAndFlush(IdentityUser.adminAccount(
                "inventory-admin@example.com", passwordEncoder.encode("A-unique-password-123")));
        return jwtService.issueAccessToken(admin);
    }
}
