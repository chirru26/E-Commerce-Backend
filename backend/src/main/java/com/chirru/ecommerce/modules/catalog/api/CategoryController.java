package com.chirru.ecommerce.modules.catalog.api;

import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.CategoryView;
import com.chirru.ecommerce.modules.catalog.application.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CatalogService catalogService;

    public CategoryController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public List<CategoryView> listCategories() {
        return catalogService.publicCategories();
    }

    @GetMapping("/{slug}")
    public CategoryView getCategory(@PathVariable String slug) {
        return catalogService.publicCategory(slug);
    }
}
