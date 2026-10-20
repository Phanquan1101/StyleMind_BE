package com.stylemind.commerce.repository.catalog;

import com.stylemind.commerce.entity.Category;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
    List<Category> findAllByOrderByNameAscCategoryIdAsc();
    @Query(value = "select exists (select 1 from public.product where category_id = :id)", nativeQuery = true)
    boolean isReferenced(@Param("id") UUID id);
}
