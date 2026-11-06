package com.stylemind.commerce.repository.catalog;

import com.stylemind.commerce.entity.Brand;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    List<Brand> findAllByOrderByNameAscBrandIdAsc();
    List<Brand> findAllByActiveTrueOrderByNameAscBrandIdAsc();
    java.util.Optional<Brand> findByBrandIdAndActiveTrue(UUID brandId);
}
