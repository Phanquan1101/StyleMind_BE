package com.stylemind.stylist.client.commerce;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Wire DTOs for the current public Commerce product and active-variant read APIs. */
public final class CommerceCatalogContracts {
    private CommerceCatalogContracts() { }

    public record VariantBatchLookupRequest(@JsonProperty("variant_ids") List<UUID> variantIds) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VariantBatchLookupResponse(List<VariantResponse> items) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductListResponse(
            List<ProductResponse> items,
            int page,
            int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("total_pages") long totalPages) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductResponse(
            @JsonProperty("product_id") UUID productId,
            @JsonProperty("category_id") UUID categoryId,
            @JsonProperty("brand_id") UUID brandId,
            @JsonProperty("category_name") String categoryName,
            @JsonProperty("brand_name") String brandName,
            String name,
            String description,
            @JsonProperty("demographic_segment") String demographicSegment,
            @JsonProperty("product_status") String productStatus,
            List<VariantResponse> variants) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VariantResponse(
            @JsonProperty("variant_id") UUID variantId,
            @JsonProperty("product_id") UUID productId,
            String sku,
            String size,
            String color,
            String material,
            @JsonProperty("unit_price_vnd") BigDecimal unitPriceVnd,
            @JsonProperty("variant_status") String variantStatus,
            List<VariantImageResponse> images) { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record VariantImageResponse(
            @JsonProperty("image_id") UUID imageId,
            @JsonProperty("variant_id") UUID variantId,
            @JsonProperty("image_url") String imageUrl,
            @JsonProperty("alt_text") String altText,
            @JsonProperty("display_order") Integer displayOrder,
            @JsonProperty("is_primary") Boolean primary) { }
}
