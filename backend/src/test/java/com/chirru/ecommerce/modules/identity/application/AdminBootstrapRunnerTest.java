package com.chirru.ecommerce.modules.identity.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import com.chirru.ecommerce.modules.identity.domain.Role;
import com.chirru.ecommerce.modules.identity.infrastructure.IdentityUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapRunnerTest {
    private final IdentityUserRepository users = mock(IdentityUserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void createsAdminOnlyWhenExplicitlyConfiguredAndAccountIsMissing() throws Exception {
        when(users.findByEmailIgnoreCase("admin@example.com")).thenReturn(java.util.Optional.empty());
        when(passwordEncoder.encode("A-unique-password-123")).thenReturn("bcrypt-hash");

        var runner = new AdminBootstrapRunner(
                users, passwordEncoder, " ADMIN@example.com ", "A-unique-password-123");
        runner.run(new DefaultApplicationArguments(new String[0]));

        var captor = org.mockito.ArgumentCaptor.forClass(IdentityUser.class);
        verify(users).saveAndFlush(captor.capture());
        assertEquals("admin@example.com", captor.getValue().getEmail());
        assertEquals(Role.ADMIN, captor.getValue().getRole());
        assertEquals("bcrypt-hash", captor.getValue().getPasswordHash());
    }

    @Test
    void refusesToPromoteAnExistingRegularUser() {
        when(users.findByEmailIgnoreCase("member@example.com"))
                .thenReturn(java.util.Optional.of(new IdentityUser("member@example.com", "already-hashed")));

        var runner = new AdminBootstrapRunner(
                users, passwordEncoder, "member@example.com", "A-unique-password-123");

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
        verify(users, never()).saveAndFlush(any(IdentityUser.class));
    }

    @Test
    void refusesBlankBootstrapCredentials() {
        var runner = new AdminBootstrapRunner(users, passwordEncoder, "", "");

        assertThrows(IllegalStateException.class,
                () -> runner.run(new DefaultApplicationArguments(new String[0])));
        verifyNoInteractions(users);
        verifyNoInteractions(passwordEncoder);
    }
}
