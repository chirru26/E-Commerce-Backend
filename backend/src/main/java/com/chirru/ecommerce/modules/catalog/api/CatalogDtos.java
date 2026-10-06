package com.chirru.ecommerce.modules.catalog.api;

import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CategoryWriteRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 120) @Pattern(regexp = "^(?:$|[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)$") String slug,
            @Size(max = 500) String description,
            Boolean active) {}

    public record CategoryView(
            UUID id, String name, String slug, String description,
            boolean active, Instant createdAt, Instant updatedAt) {}

    public record ProductWriteRequest(
            @NotBlank @Size(max = 180) String name,
            @Size(max = 200) @Pattern(regexp = "^(?:$|[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*)$") String slug,
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$") String sku,
            @Size(max = 4000) String description,
            @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Pattern(regexp = "^(?:$|[A-Z]{3})$") String currency,
            @NotNull UUID categoryId,
            ProductStatus status) {}

    public record ProductView(
            UUID id, String name, String slug, String sku, String description,
            BigDecimal price, String currency, ProductStatus status,
            UUID categoryId, String categoryName, String categorySlug,
            Instant createdAt, Instant updatedAt) {}

    public record PageResponse<T>(
            List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageResponse<T> from(org.springframework.data.domain.Page<T> result) {
            return new PageResponse<>(result.getContent(), result.getNumber(), result.getSize(),
                    result.getTotalElements(), result.getTotalPages());
        }
    }
}
