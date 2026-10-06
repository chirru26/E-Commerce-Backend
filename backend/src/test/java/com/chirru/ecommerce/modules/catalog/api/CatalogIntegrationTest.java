package com.chirru.ecommerce.modules.catalog.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import com.chirru.ecommerce.modules.catalog.infrastructure.CategoryRepository;
import com.chirru.ecommerce.modules.catalog.infrastructure.ProductRepository;
import com.chirru.ecommerce.modules.identity.application.AuthService;
import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import com.chirru.ecommerce.modules.identity.infrastructure.RefreshTokenRepository;
import com.chirru.ecommerce.security.JwtService;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class CatalogIntegrationTest {
    private static final String JWT_TEST_SECRET =
            "catalog-integration-tests-use-a-secret-at-least-32-bytes-long";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("catalog_test")
                    .withUsername("catalog_test")
                    .withPassword("catalog_test");

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
    @Autowired CatalogServiceForTests unusedServiceMarker;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired IdentityUserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;
    @Autowired AuthService authService;
    @Autowired JwtService jwtService;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        products.deleteAll();
        categories.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void publicCatalogReadsWorkWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void anonymousAndRegularUsersCannotWriteCatalog() throws Exception {
        String body = """
                {"name":"Puja Essentials","description":"Everyday puja items"}
                """;

        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());

        String userToken = authService.register("catalog-user@example.com", "A-unique-password-123").accessToken();
        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanCreateCategoryAndProductAndPublicUsersCanSearchThem() throws Exception {
        String adminToken = createAdminToken();

        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Puja Essentials","description":"Puja and worship products"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Puja Essentials"))
                .andExpect(jsonPath("$.slug").value("puja-essentials"))
                .andExpect(jsonPath("$.active").value(true));

        var category = categories.findBySlugIgnoreCase("puja-essentials").orElseThrow();
        String request = """
                {
                  "name":"Kumkum Powder",
                  "sku":"kum-001",
                  "description":"Traditional red kumkum powder",
                  "price":149.50,
                  "categoryId":"%s",
                  "status":"ACTIVE"
                }
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kumkum Powder"))
                .andExpect(jsonPath("$.slug").value("kumkum-powder"))
                .andExpect(jsonPath("$.sku").value("KUM-001"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.categorySlug").value("puja-essentials"));

        mockMvc.perform(get("/api/v1/products")
                        .param("q", "kumkum")
                        .param("category", "puja-essentials")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("KUM-001"));

        mockMvc.perform(get("/api/v1/products/kumkum-powder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price").value(149.50));
    }

    @Test
    void rejectsDuplicateSkuAndInvalidPrices() throws Exception {
        String adminToken = createAdminToken();
        createCategory(adminToken);
        var category = categories.findBySlugIgnoreCase("puja-essentials").orElseThrow();

        String validProduct = """
                {"name":"Camphor","sku":"CAM-001","price":25.00,"categoryId":"%s","status":"ACTIVE"}
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(validProduct))
                .andExpect(status().isCreated());

        String duplicateSku = """
                {"name":"Camphor Pack","sku":"cam-001","price":30.00,"categoryId":"%s","status":"ACTIVE"}
                """.formatted(category.getId());
        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(duplicateSku))
                .andExpect(status().isConflict());

        String invalidPrice = """
                {"name":"Broken Price","sku":"BAD-PRICE","price":0,"categoryId":"%s","status":"ACTIVE"}
                """.formatted(category.getId());
        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidPrice))
                .andExpect(status().isBadRequest());
    }

    @Test
    void archivedProductsDisappearFromPublicReads() throws Exception {
        String adminToken = createAdminToken();
        createCategory(adminToken);
        var category = categories.findBySlugIgnoreCase("puja-essentials").orElseThrow();
        String productJson = """
                {"name":"Incense Sticks","sku":"INC-001","price":59.00,"categoryId":"%s","status":"ACTIVE"}
                """.formatted(category.getId());

        mockMvc.perform(post("/api/v1/admin/catalog/products")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(productJson))
                .andExpect(status().isCreated());

        var product = products.findPublicBySlug("incense-sticks", ProductStatus.ACTIVE).orElseThrow();
        mockMvc.perform(delete("/api/v1/admin/catalog/products/" + product.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/products/incense-sticks"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    private String createAdminToken() {
        IdentityUser admin = users.saveAndFlush(IdentityUser.adminAccount(
                "catalog-admin@example.com", passwordEncoder.encode("A-unique-password-123")));
        return jwtService.issueAccessToken(admin);
    }

    private void createCategory(String adminToken) throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalog/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Puja Essentials","description":"Puja and worship products"}
                                """))
                .andExpect(status().isCreated());
    }

    /**
     * No-op marker keeps the test focused on the MVC surface; all setup goes through HTTP/repositories.
     */
    interface CatalogServiceForTests {}
}
