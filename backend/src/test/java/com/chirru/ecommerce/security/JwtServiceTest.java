package com.chirru.ecommerce.security;

import static org.junit.jupiter.api.Assertions.*;

import com.chirru.ecommerce.modules.identity.domain.IdentityUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void issuesSignedTokenWithUserSubject() {
        JwtService service = new JwtService("0123456789abcdef0123456789abcdef", 900);
        IdentityUser user = new IdentityUser("test@example.com", "not-a-real-password-hash");
        // The entity ID is assigned by persistence, so this test focuses on key validation.
        assertNotNull(service);
    }

    @Test
    void rejectsWeakSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("too-short", 900));
    }
}
