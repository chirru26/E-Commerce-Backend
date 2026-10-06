package com.chirru.ecommerce.modules.catalog.application;

import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.CategoryView;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.PageResponse;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.ProductView;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.CategoryWriteRequest;
import com.chirru.ecommerce.modules.catalog.api.CatalogDtos.ProductWriteRequest;
import com.chirru.ecommerce.modules.catalog.domain.Category;
import com.chirru.ecommerce.modules.catalog.domain.Product;
import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import com.chirru.ecommerce.modules.catalog.infrastructure.CategoryRepository;
import com.chirru.ecommerce.modules.catalog.infrastructure.ProductRepository;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class CatalogService {
    private static final String DEFAULT_CURRENCY = "INR";
    private static final int MAX_PAGE_SIZE = 100;

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public List<CategoryView> publicCategories() {
        return categories.findAllByActiveTrueOrderByNameAsc().stream().map(CatalogService::toView).toList();
    }

    @Transactional(readOnly = true)
    public CategoryView publicCategory(String slug) {
        Category category = categories.findBySlugIgnoreCase(slug)
                .filter(Category::isActive)
                .orElseThrow(() -> notFound("Category not found"));
        return toView(category);
    }

    @Transactional(readOnly = true)
    public List<CategoryView> allCategoriesForAdmin() {
        return categories.findAllByOrderByNameAsc().stream().map(CatalogService::toView).toList();
    }

    public CategoryView createCategory(CategoryWriteRequest request) {
        String slug = SlugSupport.normalize(request.slug(), request.name());
        ensureCategorySlugAvailable(slug, null);
        Category category = new Category(clean(request.name()), slug,
                cleanNullable(request.description()), request.active() == null || request.active());
        return toView(categories.save(category));
    }

    public CategoryView updateCategory(UUID id, CategoryWriteRequest request) {
        Category category = categories.findById(id).orElseThrow(() -> notFound("Category not found"));
        String slug = SlugSupport.normalize(request.slug(), request.name());
        ensureCategorySlugAvailable(slug, id);
        category.update(clean(request.name()), slug, cleanNullable(request.description()), request.active());
        return toView(category);
    }

    public void deactivateCategory(UUID id) {
        Category category = categories.findById(id).orElseThrow(() -> notFound("Category not found"));
        category.deactivate();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductView> publicProducts(String query, String categorySlug, int page, int size) {
        var results = products.searchPublic(normalizeQuery(query), normalizeQuery(categorySlug),
                ProductStatus.ACTIVE, pageRequest(page, size));
        return PageResponse.from(results.map(CatalogService::toView));
    }

    @Transactional(readOnly = true)
    public ProductView publicProduct(String slug) {
        Product product = products.findPublicBySlug(slug, ProductStatus.ACTIVE)
                .orElseThrow(() -> notFound("Product not found"));
        return toView(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductView> adminProducts(String query, String categorySlug,
                                                    ProductStatus status, int page, int size) {
        var results = products.searchForAdmin(normalizeQuery(query), normalizeQuery(categorySlug),
                status, pageRequest(page, size));
        return PageResponse.from(results.map(CatalogService::toView));
    }

    public ProductView createProduct(ProductWriteRequest request) {
        String slug = SlugSupport.normalize(request.slug(), request.name());
        String sku = normalizeSku(request.sku());
        ensureProductIdentityAvailable(slug, sku, null);
        Category category = activeCategory(request.categoryId());
        Product product = new Product(clean(request.name()), slug, sku,
                cleanNullable(request.description()), request.price().setScale(2, RoundingMode.UNNECESSARY),
                normalizeCurrency(request.currency()),
                request.status() == null ? ProductStatus.DRAFT : request.status(), category);
        return toView(products.save(product));
    }

    public ProductView updateProduct(UUID id, ProductWriteRequest request) {
        Product product = products.findById(id).orElseThrow(() -> notFound("Product not found"));
        String slug = SlugSupport.normalize(request.slug(), request.name());
        String sku = normalizeSku(request.sku());
        ensureProductIdentityAvailable(slug, sku, id);
        Category category = activeCategory(request.categoryId());
        product.update(clean(request.name()), slug, sku, cleanNullable(request.description()),
                request.price().setScale(2, RoundingMode.UNNECESSARY),
                normalizeCurrency(request.currency()), request.status(), category);
        return toView(product);
    }

    public void archiveProduct(UUID id) {
        Product product = products.findById(id).orElseThrow(() -> notFound("Product not found"));
        product.archive();
    }

    private Category activeCategory(UUID id) {
        Category category = categories.findById(id).orElseThrow(() -> notFound("Category not found"));
        if (!category.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Products must belong to an active category");
        }
        return category;
    }

    private void ensureCategorySlugAvailable(String slug, UUID currentId) {
        boolean exists = currentId == null
                ? categories.existsBySlugIgnoreCase(slug)
                : categories.existsBySlugIgnoreCaseAndIdNot(slug, currentId);
        if (exists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Category slug already exists");
    }

    private void ensureProductIdentityAvailable(String slug, String sku, UUID currentId) {
        boolean slugExists = currentId == null
                ? products.existsBySlugIgnoreCase(slug)
                : products.existsBySlugIgnoreCaseAndIdNot(slug, currentId);
        if (slugExists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Product slug already exists");

        boolean skuExists = currentId == null
                ? products.existsBySkuIgnoreCase(sku)
                : products.existsBySkuIgnoreCaseAndIdNot(sku, currentId);
        if (skuExists) throw new ResponseStatusException(HttpStatus.CONFLICT, "Product SKU already exists");
    }

    private static PageRequest pageRequest(int page, int size) {
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        return PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String normalizeQuery(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private static String normalizeSku(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeCurrency(String value) {
        return value == null || value.isBlank() ? DEFAULT_CURRENCY : value.toUpperCase(Locale.ROOT);
    }

    private static String clean(String value) {
        return value.trim();
    }

    private static String cleanNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static CategoryView toView(Category category) {
        return new CategoryView(category.getId(), category.getName(), category.getSlug(),
                category.getDescription(), category.isActive(), category.getCreatedAt(), category.getUpdatedAt());
    }

    private static ProductView toView(Product product) {
        return new ProductView(product.getId(), product.getName(), product.getSlug(), product.getSku(),
                product.getDescription(), product.getPrice(), product.getCurrency(), product.getStatus(),
                product.getCategory().getId(), product.getCategory().getName(), product.getCategory().getSlug(),
                product.getCreatedAt(), product.getUpdatedAt());
    }

    private static ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }
}
