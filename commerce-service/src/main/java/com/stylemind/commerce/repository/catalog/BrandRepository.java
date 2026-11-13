package com.stylemind.commerce.repository.catalog;

import com.stylemind.commerce.entity.Brand;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    List<Brand> findAllByDeletedAtIsNullOrderByNameAscBrandIdAsc();
    List<Brand> findAllByActiveTrueAndDeletedAtIsNullOrderByNameAscBrandIdAsc();
    java.util.Optional<Brand> findByBrandIdAndActiveTrueAndDeletedAtIsNull(UUID brandId);
    java.util.Optional<Brand> findByBrandIdAndDeletedAtIsNull(UUID brandId);
}
