package com.chirru.ecommerce.modules.identity.application;

import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.domain.RefreshToken;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import com.chirru.ecommerce.modules.identity.infrastructure.RefreshTokenRepository;
import com.chirru.ecommerce.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final IdentityUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final long accessTokenExpirationSeconds;
    private final long refreshExpirationSeconds;

    public AuthService(IdentityUserRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       @Value("${app.security.jwt.access-token-expiration:900}") long accessTokenExpirationSeconds,
                       @Value("${app.security.jwt.refresh-token-expiration:604800}") long refreshExpirationSeconds) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    @Transactional
    public AuthResult register(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        validatePassword(password);
        IdentityUser user = users.save(new IdentityUser(email, passwordEncoder.encode(password)));
        return createSession(user);
    }

    @Transactional
    public AuthResult login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        IdentityUser user = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!user.isEnabled() || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return createSession(user);
    }

    @Transactional
    public AuthResult refresh(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        if (stored.isRevoked() || stored.isExpired() || !stored.getUser().isEnabled()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        stored.revoke();
        return createSession(stored.getUser());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    private AuthResult createSession(IdentityUser user) {
        byte[] random = new byte[48];
        RANDOM.nextBytes(random);
        String rawRefreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        refreshTokens.save(new RefreshToken(user, hash(rawRefreshToken),
                Instant.now().plusSeconds(refreshExpirationSeconds)));
        return new AuthResult(jwtService.issueAccessToken(user), rawRefreshToken,
                "Bearer", accessTokenExpirationSeconds, user.getId().toString(), user.getEmail(), user.getRole().name());
    }

    private static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required");
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 12 and 72 characters");
        }
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }

    public record AuthResult(String accessToken, String refreshToken, String tokenType,
                             long expiresIn, String userId, String email, String role) {}
}
