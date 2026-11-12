package com.stylemind.commerce.service.product;

import com.stylemind.commerce.dto.product.ProductCommand;
import com.stylemind.commerce.dto.product.ProductListCriteria;
import com.stylemind.commerce.dto.product.ProductListResponse;
import com.stylemind.commerce.dto.product.ProductResponse;
import com.stylemind.commerce.dto.product.VariantBatchLookupResponse;
import com.stylemind.commerce.dto.product.VariantCommand;
import com.stylemind.commerce.dto.product.VariantImageResponse;
import com.stylemind.commerce.dto.product.VariantResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface ProductService {

    ProductResponse createProduct(ProductCommand command);

    ProductResponse replaceProduct(UUID productId, ProductCommand command);

    void deleteProduct(UUID productId);

    ProductListResponse listPublicProducts(ProductListCriteria criteria);

    ProductListResponse listAdminProducts(ProductListCriteria criteria);

    ProductResponse getPublicProduct(UUID productId);

    ProductResponse getAdminProduct(UUID productId);

    VariantResponse createVariant(UUID productId, VariantCommand command);

    VariantResponse replaceVariant(UUID variantId, VariantCommand command);

    void deleteVariant(UUID variantId);

    VariantBatchLookupResponse activeVariantLookup(List<UUID> variantIds);

    VariantImageResponse uploadImage(UUID variantId, MultipartFile file, String altText);

    List<VariantImageResponse> reorderImages(UUID variantId, Map<UUID, Integer> order, UUID primaryImageId);

    void deleteImage(UUID imageId);
}
