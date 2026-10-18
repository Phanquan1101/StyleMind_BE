package com.stylemind.commerce.dto.product;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ImageReorderRequest(
        List<ImageOrderItem> items,
        @JsonProperty("primary_image_id") String primaryImageId) {

    public record ImageOrderItem(
            @JsonProperty("image_id") String imageId,
            @JsonProperty("display_order") Integer displayOrder) {
    }
}
