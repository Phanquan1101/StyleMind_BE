package com.stylemind.commerce.dto.dashboard;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Admin dashboard payload. Money is VND. All dates are calendar days in {@code timezone}.
 *
 * <p>Revenue definition (single source of truth, see FIX_LOG): money actually collected, counted on the day it
 * was received. A payment counts when payment_event_inbox holds an APPLIED event for the order whose method,
 * amount and customer match the order (the same rule the order's PAID status uses: QR/VNPAY paid, COD collected).
 * CANCELLED orders are excluded entirely; refunds (refund_result_inbox) are subtracted on the day they happened.
 */
public record DashboardResponse(
        String timezone,
        LocalDate from,
        LocalDate to,
        int days,
        @JsonProperty("previous_from") LocalDate previousFrom,
        @JsonProperty("previous_to") LocalDate previousTo,
        @JsonProperty("generated_at") OffsetDateTime generatedAt,
        Kpis kpis,
        @JsonProperty("revenue_by_day") List<DayPoint> revenueByDay,
        @JsonProperty("orders_by_status") List<StatusCount> ordersByStatus,
        @JsonProperty("revenue_by_payment_method") List<MethodRevenue> revenueByPaymentMethod,
        @JsonProperty("top_products") List<TopProduct> topProducts,
        Pending pending,
        @JsonProperty("recent_orders") List<RecentOrder> recentOrders) {

    public record Kpis(Kpi current, Kpi previous) {
    }

    /** placed = orders created in the period (any status); paid = orders whose money was collected in the period. */
    public record Kpi(
            @JsonProperty("revenue_vnd") long revenueVnd,
            @JsonProperty("placed_order_count") long placedOrderCount,
            @JsonProperty("paid_order_count") long paidOrderCount,
            @JsonProperty("average_order_value_vnd") long averageOrderValueVnd) {
    }

    public record DayPoint(
            LocalDate date,
            @JsonProperty("revenue_vnd") long revenueVnd,
            @JsonProperty("paid_order_count") long paidOrderCount,
            @JsonProperty("placed_order_count") long placedOrderCount) {
    }

    public record StatusCount(String status, long count) {
    }

    public record MethodRevenue(@JsonProperty("payment_method") String paymentMethod,
            @JsonProperty("revenue_vnd") long revenueVnd) {
    }

    public record TopProduct(
            @JsonProperty("product_id") UUID productId,
            String name,
            @JsonProperty("image_url") String imageUrl,
            long quantity,
            @JsonProperty("revenue_vnd") long revenueVnd) {
    }

    public record Pending(
            @JsonProperty("awaiting_confirmation_orders") long awaitingConfirmationOrders,
            @JsonProperty("pending_cancellation_requests") long pendingCancellationRequests,
            @JsonProperty("low_stock_variants") long lowStockVariants) {
    }

    public record RecentOrder(
            @JsonProperty("order_id") UUID orderId,
            @JsonProperty("order_number") String orderNumber,
            @JsonProperty("customer_name") String customerName,
            @JsonProperty("total_vnd") long totalVnd,
            @JsonProperty("order_status") String orderStatus,
            @JsonProperty("confirmation_state") String confirmationState,
            boolean paid,
            @JsonProperty("placed_at") String placedAt) {
    }
}
