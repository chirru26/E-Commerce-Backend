package com.chirru.ecommerce.modules.catalog.api;

import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.CategoryView;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.CategoryWriteRequest;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.PageResponse;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.ProductView;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.ProductWriteRequest;
import com.chirru.ecommerce.modules.catalog.application.CatalogService;
import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@Validated
public class AdminCatalogController {
    private final CatalogService catalogService;

    public AdminCatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/categories")
    public List<CategoryView> listAllCategories() {
        return catalogService.allCategoriesForAdmin();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CategoryWriteRequest request) {
        return catalogService.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryWriteRequest request) {
        return catalogService.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateCategory(@PathVariable UUID id) {
        catalogService.deactivateCategory(id);
    }

    @GetMapping("/products")
    public PageResponse<ProductView> listProducts(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(required = false) @Size(max = 120) String category,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return catalogService.adminProducts(q, category, status, page, size);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView createProduct(@Valid @RequestBody ProductWriteRequest request) {
        return catalogService.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public ProductView updateProduct(@PathVariable UUID id, @Valid @RequestBody ProductWriteRequest request) {
        return catalogService.updateProduct(id, request);
    }

    @DeleteMapping("/products/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveProduct(@PathVariable UUID id) {
        catalogService.archiveProduct(id);
    }
}
