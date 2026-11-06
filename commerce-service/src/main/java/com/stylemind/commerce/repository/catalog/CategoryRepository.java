package com.stylemind.commerce.repository.catalog;

import com.stylemind.commerce.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByOrderByNameAscCategoryIdAsc();
    List<Category> findAllByActiveTrueOrderByNameAscCategoryIdAsc();
    java.util.Optional<Category> findByCategoryIdAndActiveTrue(UUID categoryId);
}
