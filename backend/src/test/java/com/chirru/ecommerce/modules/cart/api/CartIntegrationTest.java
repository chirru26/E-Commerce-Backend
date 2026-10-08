package com.chirru.ecommerce.modules.cart.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chirru.ecommerce.modules.cart.api.CartDtos.AddCartItemRequest;
import com.chirru.ecommerce.modules.cart.api.CartDtos.UpdateCartItemRequest;
import com.chirru.ecommerce.modules.cart.application.CartService;
import com.chirru.ecommerce.modules.cart.domain.CartStatus;
import com.chirru.ecommerce.modules.cart.infrastructure.CartItemRepository;
import com.chirru.ecommerce.modules.cart.infrastructure.CartRepository;
import com.chirru.ecommerce.modules.catalog.domain.Category;
import com.chirru.ecommerce.modules.catalog.domain.Product;
import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import com.chirru.ecommerce.modules.catalog.infrastructure.CategoryRepository;
import com.chirru.ecommerce.modules.catalog.infrastructure.ProductRepository;
import com.chirru.ecommerce.modules.identity.application.AuthService;
import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import com.chirru.ecommerce.modules.identity.infrastructure.RefreshTokenRepository;
import com.chirru.ecommerce.modules.inventory.api.InventoryDtos.InventoryCreateRequest;
import com.chirru.ecommerce.modules.inventory.application.InventoryService;
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
class CartIntegrationTest {
    private static final String JWT_TEST_SECRET =
            "cart-integration-tests-use-a-secret-at-least-32-bytes-long";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("cart_test")
                    .withUsername("cart_test")
                    .withPassword("cart_test");

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
    @Autowired IdentityUserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired AuthService authService;
    @Autowired CartService cartService;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryStockRepository stocks;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryMovementRepository movements;

    @BeforeEach
    void cleanDatabase() {
        cartItems.deleteAll();
        carts.deleteAll();
        movements.deleteAll();
        reservations.deleteAll();
        stocks.deleteAll();
        products.deleteAll();
        categories.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void anonymousCannotAccessCartAndAuthenticatedUserGetsAnEmptyCart() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized());

        String token = authService.register("cart-user@example.com", "A-unique-password-123").accessToken();

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.itemCount").value(0))
                .andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    void userCanAddUpdateRemoveAndClearCartItems() throws Exception {
        Product product = createProduct("Incense Sticks", "INC-001", "ACTIVE");
        String token = authService.register("cart-flow@example.com", "A-unique-password-123").accessToken();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":2}
                                """.formatted(product.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sku").value("INC-001"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].unitPrice").value(99.00))
                .andExpect(jsonPath("$.subtotal").value(198.00));

        mockMvc.perform(put("/api/v1/cart/items/" + product.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":4}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(4))
                .andExpect(jsonPath("$.subtotal").value(396.00));

        mockMvc.perform(delete("/api/v1/cart/items/" + product.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":1}
                                """.formatted(product.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/cart")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rejectsUnknownAndInactiveProductsAndInvalidQuantities() throws Exception {
        String token = authService.register("cart-validation@example.com", "A-unique-password-123").accessToken();
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":1}
                                """.formatted(missing)))
                .andExpect(status().isNotFound());

        Product draft = createProduct("Draft Product", "DR-001", "DRAFT");
        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":1}
                                """.formatted(draft.getId())))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/api/v1/cart/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"productId":"%s","quantity":0}
                                """.formatted(missing)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void checkoutPreparationReservesInventoryAndCanBeReleasedOrCompleted() throws Exception {
        Product product = createProduct("Camphor", "CAM-001", "ACTIVE");
        inventoryService.initializeInventory(new InventoryCreateRequest(product.getId(), 10, 1));

        IdentityUser user = users.saveAndFlush(new IdentityUser(
                "checkout@example.com", passwordEncoder.encode("A-unique-password-123")));
        String token = jwtService.issueAccessToken(user);

        cartService.addItem(user.getId(), new AddCartItemRequest(product.getId(), 6));

        var prepared = cartService.prepareForCheckout(user.getId(), "order-100");
        assertEquals(1, prepared.items().size());
        assertEquals(6, prepared.items().getFirst().quantity());

        var reservation = reservations.findById(prepared.items().getFirst().reservationId()).orElseThrow();
        assertEquals("ACTIVE", reservation.getStatus().name());
        assertEquals(4, stocks.findByProductId(product.getId()).orElseThrow().availableQuantity());

        ResponseStatusException editError = assertThrows(ResponseStatusException.class,
                () -> cartService.updateItem(user.getId(), product.getId(),
                        new UpdateCartItemRequest(2)));
        assertEquals(409, editError.getStatusCode().value());

        var preparedRepeat = cartService.prepareForCheckout(user.getId(), "order-100");
        assertEquals(prepared.items().getFirst().reservationId(),
                preparedRepeat.items().getFirst().reservationId());

        cartService.releaseCheckout(user.getId(), "order-100");
        assertEquals(CartStatus.ACTIVE, carts.findById(prepared.cartId()).orElseThrow().getStatus());
        assertEquals(10, stocks.findByProductId(product.getId()).orElseThrow().availableQuantity());

        var preparedAgain = cartService.prepareForCheckout(user.getId(), "order-101");
        cartService.completeCheckout(user.getId(), "order-101");
        assertEquals(CartStatus.CHECKED_OUT, carts.findById(preparedAgain.cartId()).orElseThrow().getStatus());

        var stock = stocks.findByProductId(product.getId()).orElseThrow();
        assertEquals(4, stock.getOnHand());
        assertEquals(0, stock.getReserved());

        mockMvc.perform(get("/api/v1/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    private Product createProduct(String name, String sku, String status) {
        Category category = categories.saveAndFlush(
                new Category("Puja Essentials", "puja-essentials-" + sku.toLowerCase(), "Puja products", true));
        return products.saveAndFlush(new Product(
                name, name.toLowerCase().replace(' ', '-') + "-" + sku.toLowerCase(),
                sku, "Cart test product", new BigDecimal("99.00"), "INR",
                ProductStatus.valueOf(status), category));
    }
}
