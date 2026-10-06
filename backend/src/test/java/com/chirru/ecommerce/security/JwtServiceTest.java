package com.chirru.ecommerce.security;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

class JwtServiceTest {
    @Test
    void rejectsWeakSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtService("too-short", 900));
    }

    @Test
    void rejectsMalformedToken() {
        JwtService service = new JwtService("0123456789abcdef0123456789abcdef", 900);
        assertThrows(JwtException.class, () -> service.parseIdentity("not-a-jwt"));
    }
}
