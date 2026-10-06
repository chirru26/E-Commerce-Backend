package com.chirru.ecommerce.modules.catalog.infrastructure;

import com.chirru.ecommerce.modules.catalog.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    Optional<Category> findBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);
    List<Category> findAllByActiveTrueOrderByNameAsc();
    List<Category> findAllByOrderByNameAsc();
}
