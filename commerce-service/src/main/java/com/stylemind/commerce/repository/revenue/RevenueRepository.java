package com.stylemind.commerce.repository.revenue;

import com.stylemind.commerce.entity.Order;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Aggregates for the revenue report. "Collected" and the time zone follow DashboardRepository: an APPLIED
 * payment_event_inbox row matching the order's method, total and customer, on a non-cancelled order, bucketed by
 * confirmed_at in Asia/Ho_Chi_Minh. Refunds come from refund_result_inbox on non-cancelled orders.
 */
public interface RevenueRepository extends JpaRepository<Order, UUID> {

    interface DailyRow {
        String getDay();
        String getPaymentMethod();
        Long getPaidVnd();
        Long getRefundVnd();
        Long getPaidOrders();
    }

    interface CategoryRow {
        UUID getCategoryId();
        String getName();
        Long getRevenueVnd();
        Long getQuantity();
    }

    interface ProductRow {
        UUID getProductId();
        String getName();
        String getImageUrl();
        Long getQuantity();
        Long getRevenueVnd();
    }

    /** Collected money, refunds and collected-order counts per (day, method). */
    @Query(value = """
            SELECT to_char(t.day, 'YYYY-MM-DD') AS "day", t.payment_method AS "paymentMethod",
                   SUM(t.paid)::bigint AS "paidVnd", SUM(t.refund)::bigint AS "refundVnd", SUM(t.orders)::bigint AS "paidOrders"
            FROM (
              SELECT (e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day, o.payment_method,
                     e.amount_vnd AS paid, 0 AS refund, 1 AS orders
              FROM payment_event_inbox e JOIN customer_order o ON o.order_id = e.order_id
              WHERE e.outcome = 'APPLIED' AND o.order_status <> 'CANCELLED'
                AND e.payment_method = o.payment_method AND e.amount_vnd = o.accepted_total_vnd
                AND e.customer_id IS NOT DISTINCT FROM o.customer_id
                AND e.confirmed_at >= :fromTs AND e.confirmed_at < :toTs
              UNION ALL
              SELECT (r.refunded_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, o.payment_method, 0, r.amount_vnd, 0
              FROM refund_result_inbox r JOIN customer_order o ON o.order_id = r.order_id
              WHERE o.order_status <> 'CANCELLED' AND r.refunded_at >= :fromTs AND r.refunded_at < :toTs
            ) t
            GROUP BY t.day, t.payment_method ORDER BY t.day
            """, nativeQuery = true)
    List<DailyRow> dailyRows(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    /** Value of the lines of orders collected in the period, per category. */
    @Query(value = """
            SELECT c.category_id AS "categoryId", c.name AS "name",
                   SUM(i.quantity * i.unit_price_vnd)::bigint AS "revenueVnd", SUM(i.quantity)::bigint AS "quantity"
            FROM order_item i
              JOIN product_variant v ON v.variant_id = i.variant_id
              JOIN product p ON p.product_id = v.product_id
              JOIN category c ON c.category_id = p.category_id
            WHERE i.order_id IN (
              SELECT o.order_id FROM payment_event_inbox e JOIN customer_order o ON o.order_id = e.order_id
              WHERE e.outcome = 'APPLIED' AND o.order_status <> 'CANCELLED'
                AND e.payment_method = o.payment_method AND e.amount_vnd = o.accepted_total_vnd
                AND e.customer_id IS NOT DISTINCT FROM o.customer_id
                AND e.confirmed_at >= :fromTs AND e.confirmed_at < :toTs)
            GROUP BY c.category_id, c.name
            ORDER BY SUM(i.quantity * i.unit_price_vnd) DESC, c.name
            """, nativeQuery = true)
    List<CategoryRow> categoryRows(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    /** Best sellers among orders collected in the period, by quantity then value. */
    @Query(value = """
            SELECT v.product_id AS "productId", MAX(i.product_name_snapshot) AS "name",
                   COALESCE((ARRAY_AGG(i.image_url_snapshot ORDER BY i.order_item_id) FILTER (WHERE i.image_url_snapshot IS NOT NULL))[1],
                            (SELECT a.image_url FROM variant_image vi
                               JOIN image_asset a ON a.image_asset_id = vi.image_asset_id
                               JOIN product_variant pv ON pv.variant_id = vi.variant_id
                              WHERE pv.product_id = v.product_id
                              ORDER BY vi.is_primary DESC, vi.display_order, vi.image_id LIMIT 1)) AS "imageUrl",
                   SUM(i.quantity)::bigint AS "quantity", SUM(i.quantity * i.unit_price_vnd)::bigint AS "revenueVnd"
            FROM order_item i JOIN product_variant v ON v.variant_id = i.variant_id
            WHERE i.order_id IN (
              SELECT o.order_id FROM payment_event_inbox e JOIN customer_order o ON o.order_id = e.order_id
              WHERE e.outcome = 'APPLIED' AND o.order_status <> 'CANCELLED'
                AND e.payment_method = o.payment_method AND e.amount_vnd = o.accepted_total_vnd
                AND e.customer_id IS NOT DISTINCT FROM o.customer_id
                AND e.confirmed_at >= :fromTs AND e.confirmed_at < :toTs)
            GROUP BY v.product_id
            ORDER BY SUM(i.quantity) DESC, SUM(i.quantity * i.unit_price_vnd) DESC, MAX(i.product_name_snapshot)
            LIMIT :limit
            """, nativeQuery = true)
    List<ProductRow> topProducts(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs,
            @Param("limit") int limit);
}
