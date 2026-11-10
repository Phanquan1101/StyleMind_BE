package com.stylemind.commerce.repository.product;

import com.stylemind.commerce.entity.ImageAsset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, UUID> { }
