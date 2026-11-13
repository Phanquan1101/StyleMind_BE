package com.stylemind.commerce.dto.revenue;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detailed revenue report. Same revenue definition as the overview dashboard: money actually collected, on the day it
 * was received (Asia/Ho_Chi_Minh), cancelled orders excluded, refunds subtracted on the day they happened.
 * {@code revenue_vnd} = collected, {@code refunded_vnd} = refunded, {@code net_revenue_vnd} = collected - refunded
 * (this net figure is the dashboard's "revenue"). Periods partition [from, to], so every grouping has the same totals.
 */
public record RevenueReportResponse(
        String timezone,
        LocalDate from,
        LocalDate to,
        int days,
        @JsonProperty("previous_from") LocalDate previousFrom,
        @JsonProperty("previous_to") LocalDate previousTo,
        String group,
        @JsonProperty("generated_at") OffsetDateTime generatedAt,
        Totals totals,
        List<PeriodRow> periods,
        @JsonProperty("by_category") List<CategoryRevenue> byCategory,
        @JsonProperty("by_payment_method") List<MethodRevenue> byPaymentMethod,
        @JsonProperty("top_products") List<TopProduct> topProducts) {

    public record Metrics(
            @JsonProperty("paid_order_count") long paidOrderCount,
            @JsonProperty("revenue_vnd") long revenueVnd,
            @JsonProperty("refunded_vnd") long refundedVnd,
            @JsonProperty("net_revenue_vnd") long netRevenueVnd) {

        public static Metrics of(long paidOrders, long revenue, long refunded) {
            return new Metrics(paidOrders, revenue, refunded, revenue - refunded);
        }
    }

    public record Totals(Metrics current, Metrics previous) {
    }

    /**
     * One bucket of the chosen grouping, clipped to the requested range. {@code previous} is the same-sized window
     * shifted back by the length of the whole range, so period i lines up with period i of the previous range.
     */
    public record PeriodRow(
            LocalDate from,
            LocalDate to,
            @JsonProperty("previous_from") LocalDate previousFrom,
            @JsonProperty("previous_to") LocalDate previousTo,
            Metrics current,
            Metrics previous) {
    }

    /** Value of order lines sold in the period (before refunds, which are not attributable to a category). */
    public record CategoryRevenue(
            @JsonProperty("category_id") UUID categoryId,
            String name,
            @JsonProperty("revenue_vnd") long revenueVnd,
            long quantity) {
    }

    public record MethodRevenue(
            @JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("revenue_vnd") long revenueVnd,
            @JsonProperty("refunded_vnd") long refundedVnd,
            @JsonProperty("net_revenue_vnd") long netRevenueVnd,
            @JsonProperty("paid_order_count") long paidOrderCount) {
    }

    public record TopProduct(
            @JsonProperty("product_id") UUID productId,
            String name,
            @JsonProperty("image_url") String imageUrl,
            long quantity,
            @JsonProperty("revenue_vnd") long revenueVnd) {
    }
}
