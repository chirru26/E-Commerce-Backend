package com.chirru.ecommerce.modules.identity.application;

import static org.junit.jupiter.api.Assertions.*;

import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import com.chirru.ecommerce.modules.identity.infrastructure.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AuthServiceIntegrationTest {

    private static final String JWT_TEST_SECRET =
            "test-secret-for-auth-integration-tests-minimum-32-bytes";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:17-alpine")
                    .withDatabaseName("ecommerce_test")
                    .withUsername("ecommerce_test")
                    .withPassword("ecommerce_test");

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

    @Autowired
    AuthService authService;

    @Autowired
    IdentityUserRepository users;

    @Autowired
    RefreshTokenRepository refreshTokens;

    @BeforeEach
    void cleanDatabase() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void registersUserWithHashedPasswordAndTokenPair() {
        var result = authService.register(" Chirru@example.com ", "A-unique-password-123");

        assertEquals("chirru@example.com", result.email());
        assertEquals("USER", result.role());
        assertEquals("Bearer", result.tokenType());
        assertEquals(900, result.expiresIn());
        assertNotNull(result.accessToken());
        assertFalse(result.accessToken().isBlank());
        assertFalse(result.refreshToken().isBlank());

        var user = users.findByEmailIgnoreCase("chirru@example.com").orElseThrow();
        assertNotEquals("A-unique-password-123", user.getPasswordHash());
        assertTrue(user.getPasswordHash().startsWith("$2"));
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        authService.register("duplicate@example.com", "A-unique-password-123");

        var exception = assertThrows(ResponseStatusException.class,
                () -> authService.register("DUPLICATE@example.com", "A-unique-password-456"));

        assertEquals(409, exception.getStatusCode().value());
    }

    @Test
    void logsInWithCorrectPasswordAndRejectsIncorrectPassword() {
        authService.register("login@example.com", "A-unique-password-123");

        assertEquals("login@example.com",
                authService.login("LOGIN@example.com", "A-unique-password-123").email());

        assertThrows(BadCredentialsException.class,
                () -> authService.login("login@example.com", "not-the-correct-password"));
    }

    @Test
    void refreshRotatesTokenAndRejectsReuseOfOldToken() {
        var initial = authService.register("refresh@example.com", "A-unique-password-123");

        var refreshed = authService.refresh(initial.refreshToken());

        assertNotEquals(initial.refreshToken(), refreshed.refreshToken());
        assertNotEquals(initial.accessToken(), refreshed.accessToken());
        assertThrows(BadCredentialsException.class,
                () -> authService.refresh(initial.refreshToken()));
    }

    @Test
    void logoutRevokesRefreshToken() {
        var session = authService.register("logout@example.com", "A-unique-password-123");

        authService.logout(session.refreshToken());

        assertThrows(BadCredentialsException.class,
                () -> authService.refresh(session.refreshToken()));
    }

    @Test
    void rejectsPasswordOutsidePolicy() {
        assertThrows(ResponseStatusException.class,
                () -> authService.register("weak@example.com", "short"));
        assertFalse(users.existsByEmailIgnoreCase("weak@example.com"));
    }
}
