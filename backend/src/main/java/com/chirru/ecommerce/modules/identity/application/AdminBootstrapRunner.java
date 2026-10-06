package com.chirru.ecommerce.modules.identity.application;

import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.domain.Role;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(prefix = "app.security.admin-bootstrap", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private final IdentityUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String configuredEmail;
    private final String configuredPassword;

    public AdminBootstrapRunner(IdentityUserRepository users,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.security.admin-bootstrap.email:}") String configuredEmail,
                                @Value("${app.security.admin-bootstrap.password:}") String configuredPassword) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = normalizeAndValidateEmail(configuredEmail);
        validatePassword(configuredPassword);

        var existing = users.findByEmailIgnoreCase(email);
        if (existing.isPresent()) {
            if (existing.get().getRole() == Role.ADMIN && existing.get().isEnabled()) {
                return;
            }
            throw new IllegalStateException(
                    "Admin bootstrap email already belongs to a non-admin or disabled account; no role change was made");
        }

        users.saveAndFlush(IdentityUser.adminAccount(email, passwordEncoder.encode(configuredPassword)));
    }

    private static String normalizeAndValidateEmail(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL is required when admin bootstrap is enabled");
        }
        String email = value.trim().toLowerCase(Locale.ROOT);
        if (email.length() > 254 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_EMAIL must be a valid email address");
        }
        return email;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 72
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_PASSWORD must be between 12 and 72 characters and at most 72 UTF-8 bytes");
        }
    }
}
