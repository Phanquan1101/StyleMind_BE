package com.stylemind.commerce.dto.returns;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ReturnResponse(
        @JsonProperty("return_id") UUID returnId,
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("order_number") String orderNumber,
        @JsonProperty("status") Status status,
        @JsonProperty("reason") String reason,
        @JsonProperty("decision_reason") String decisionReason,
        @JsonProperty("amount_vnd") long amountVnd,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        @JsonProperty("items") List<Item> items,
        @JsonProperty("evidence") List<Evidence> evidence,
        @JsonProperty("history") List<History> history) {

    public enum Status {
        REQUESTED, MORE_IMAGES_REQUESTED, PICKUP_APPROVED, REJECTED, PENDING_REFUND, REFUNDED
    }

    public record Item(
            @JsonProperty("order_item_id") UUID orderItemId,
            @JsonProperty("product_name") String productName,
            @JsonProperty("sku_snapshot") String skuSnapshot,
            @JsonProperty("color_snapshot") String colorSnapshot,
            @JsonProperty("size_snapshot") String sizeSnapshot,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("unit_price_vnd") long unitPriceVnd) { }

    public record Evidence(@JsonProperty("image_id") UUID imageId) { }

    public record History(
            @JsonProperty("status") Status status,
            @JsonProperty("reason") String reason,
            @JsonProperty("changed_at") OffsetDateTime changedAt) { }

    public record OrderReturns(
            @JsonProperty("eligible") boolean eligible,
            @JsonProperty("available_items") List<Item> availableItems,
            @JsonProperty("items") List<ReturnResponse> items,
            @JsonProperty("ineligible_reason") String ineligibleReason) { }

    public record AdminPage(
            @JsonProperty("items") List<ReturnResponse> items,
            @JsonProperty("page") int page,
            @JsonProperty("size") int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("total_pages") long totalPages) { }
}
