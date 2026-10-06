package com.chirru.ecommerce.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_products", uniqueConstraints = {
        @UniqueConstraint(name = "uk_catalog_products_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uk_catalog_products_sku", columnNames = "sku")
})
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 200)
    private String slug;

    @Column(nullable = false, length = 64)
    private String sku;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() {}

    public Product(String name, String slug, String sku, String description,
                   BigDecimal price, String currency, ProductStatus status, Category category) {
        this.name = name;
        this.slug = slug;
        this.sku = sku;
        this.description = description;
        this.price = price;
        this.currency = currency;
        this.status = status == null ? ProductStatus.DRAFT : status;
        this.category = category;
    }

    public void update(String name, String slug, String sku, String description,
                       BigDecimal price, String currency, ProductStatus status, Category category) {
        this.name = name;
        this.slug = slug;
        this.sku = sku;
        this.description = description;
        this.price = price;
        this.currency = currency;
        if (status != null) this.status = status;
        this.category = category;
    }

    public void archive() {
        this.status = ProductStatus.ARCHIVED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public String getSku() { return sku; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public ProductStatus getStatus() { return status; }
    public Category getCategory() { return category; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
