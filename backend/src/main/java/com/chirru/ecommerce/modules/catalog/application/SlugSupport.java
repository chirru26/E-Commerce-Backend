package com.chirru.ecommerce.modules.catalog.application;

import java.text.Normalizer;
import java.util.Locale;

final class SlugSupport {
    private SlugSupport() {}

    static String from(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    static String normalize(String value, String fallback) {
        String result = value == null || value.isBlank() ? from(fallback) : from(value);
        if (result.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "A usable name or slug is required");
        }
        return result;
    }
}
