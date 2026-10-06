package com.chirru.ecommerce.modules.catalog.infrastructure;

import com.chirru.ecommerce.modules.catalog.domain.Product;
import com.chirru.ecommerce.modules.catalog.domain.ProductStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);
    boolean existsBySkuIgnoreCase(String sku);
    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);
    boolean existsByCategory_Id(UUID categoryId);

    @Query("""
            select p from Product p join p.category c
            where p.status = :activeStatus and c.active = true
              and (:query is null or lower(p.name) like lower(concat('%', :query, '%'))
                   or lower(p.sku) like lower(concat('%', :query, '%')))
              and (:categorySlug is null or lower(c.slug) = lower(:categorySlug))
            """)
    Page<Product> searchPublic(@Param("query") String query,
                               @Param("categorySlug") String categorySlug,
                               @Param("activeStatus") ProductStatus activeStatus,
                               Pageable pageable);

    @Query("""
            select p from Product p join p.category c
            where (:query is null or lower(p.name) like lower(concat('%', :query, '%'))
                   or lower(p.sku) like lower(concat('%', :query, '%')))
              and (:categorySlug is null or lower(c.slug) = lower(:categorySlug))
              and (:status is null or p.status = :status)
            """)
    Page<Product> searchForAdmin(@Param("query") String query,
                                 @Param("categorySlug") String categorySlug,
                                 @Param("status") ProductStatus status,
                                 Pageable pageable);

    @Query("""
            select p from Product p join p.category c
            where lower(p.slug) = lower(:slug)
              and p.status = :activeStatus and c.active = true
            """)
    Optional<Product> findPublicBySlug(@Param("slug") String slug,
                                       @Param("activeStatus") ProductStatus activeStatus);
}
