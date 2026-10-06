package com.chirru.ecommerce.modules.catalog.api;

import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.PageResponse;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.ProductView;
import com.chirru.ecommerce.modules.catalog.application.CatalogService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
@Validated
public class ProductController {
    private final CatalogService catalogService;

    public ProductController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public PageResponse<ProductView> listProducts(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 120) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogService.publicProducts(q, category, page, size);
    }

    @GetMapping("/{slug}")
    public ProductView getProduct(@PathVariable String slug) {
        return catalogService.publicProduct(slug);
    }
}
