package com.stylemind.commerce.repository.dashboard;

import com.stylemind.commerce.entity.Order;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only aggregates for the admin dashboard. Everything is computed in SQL; no order rows are loaded.
 *
 * <p>"Collected" = an APPLIED payment_event_inbox row that matches the order's method, total and customer (the same
 * predicate OrderRepository.paymentProgress uses for PAID), on a non-cancelled order, bucketed by confirmed_at in the
 * shop time zone (Asia/Ho_Chi_Minh). Refunds come from refund_result_inbox on non-cancelled orders.
 */
public interface DashboardRepository extends JpaRepository<Order, UUID> {

    interface RevenueRow {
        String getDay();
        String getPaymentMethod();
        Long getRevenueVnd();
        Long getPaidOrders();
    }

    interface PlacedRow {
        String getDay();
        Long getPlaced();
    }

    interface StatusRow {
        String getStatus();
        Long getCount();
    }

    interface TopProductRow {
        UUID getProductId();
        String getName();
        String getImageUrl();
        Long getQuantity();
        Long getRevenueVnd();
    }

    interface RecentOrderRow {
        UUID getOrderId();
        String getOrderNumber();
        String getCustomerName();
        Long getTotalVnd();
        String getOrderStatus();
        String getConfirmationState();
        Boolean getPaid();
        String getPlacedAt();
    }

    /** Net money per (day, method): collected payments minus refunds, plus the number of orders collected. */
    @Query(value = """
            SELECT to_char(t.day, 'YYYY-MM-DD') AS "day", t.payment_method AS "paymentMethod",
                   SUM(t.amount)::bigint AS "revenueVnd", SUM(t.paid)::bigint AS "paidOrders"
            FROM (
              SELECT (e.confirmed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS day, o.payment_method,
                     e.amount_vnd AS amount, 1 AS paid
              FROM payment_event_inbox e JOIN customer_order o ON o.order_id = e.order_id
              WHERE e.outcome = 'APPLIED' AND o.order_status <> 'CANCELLED'
                AND e.payment_method = o.payment_method AND e.amount_vnd = o.accepted_total_vnd
                AND e.customer_id IS NOT DISTINCT FROM o.customer_id
                AND e.confirmed_at >= :fromTs AND e.confirmed_at < :toTs
              UNION ALL
              SELECT (r.refunded_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, o.payment_method, -r.amount_vnd, 0
              FROM refund_result_inbox r JOIN customer_order o ON o.order_id = r.order_id
              WHERE o.order_status <> 'CANCELLED' AND r.refunded_at >= :fromTs AND r.refunded_at < :toTs
            ) t
            GROUP BY t.day, t.payment_method ORDER BY t.day
            """, nativeQuery = true)
    List<RevenueRow> revenueRows(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    /** Orders created per shop-local day (every status). */
    @Query(value = """
            SELECT to_char((o.placed_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date, 'YYYY-MM-DD') AS "day",
                   COUNT(*)::bigint AS "placed"
            FROM customer_order o WHERE o.placed_at >= :fromTs AND o.placed_at < :toTs
            GROUP BY 1 ORDER BY 1
            """, nativeQuery = true)
    List<PlacedRow> placedRows(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    /** Orders created in the period by display bucket (PLACED/PAID are split by whether the shop confirmed them). */
    @Query(value = """
            SELECT CASE WHEN o.order_status IN ('PLACED', 'PAID')
                          THEN CASE WHEN o.confirmation_state = 'CONFIRMED' THEN 'CONFIRMED' ELSE 'AWAITING_CONFIRMATION' END
                        ELSE o.order_status END AS "status",
                   COUNT(*)::bigint AS "count"
            FROM customer_order o WHERE o.placed_at >= :fromTs AND o.placed_at < :toTs
            GROUP BY 1
            """, nativeQuery = true)
    List<StatusRow> statusRows(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    /** Best sellers among orders collected in the period (the same orders that make up revenue), by quantity then value. */
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
            LIMIT 5
            """, nativeQuery = true)
    List<TopProductRow> topProducts(@Param("fromTs") OffsetDateTime fromTs, @Param("toTs") OffsetDateTime toTs);

    /** Placed/paid but not yet confirmed by the shop (what the admin "Chờ xác nhận" filter lists). */
    @Query(value = """
            SELECT COUNT(*) FROM customer_order
            WHERE order_status IN ('PLACED', 'PAID') AND confirmation_state <> 'CONFIRMED'
            """, nativeQuery = true)
    long countAwaitingConfirmation();

    @Query(value = "SELECT COUNT(*) FROM cancellation_request WHERE request_status = 'PENDING'", nativeQuery = true)
    long countPendingCancellationRequests();

    /** Variants on sale (variant and product ACTIVE) with at most {@code threshold} units left. */
    @Query(value = """
            SELECT COUNT(*) FROM product_variant v JOIN product p ON p.product_id = v.product_id
            WHERE v.variant_status = 'ACTIVE' AND p.product_status = 'ACTIVE' AND v.quantity <= :threshold
            """, nativeQuery = true)
    long countLowStockVariants(@Param("threshold") int threshold);

    @Query(value = """
            SELECT o.order_id AS "orderId", o.order_number AS "orderNumber", a.recipient_name AS "customerName",
                   o.accepted_total_vnd AS "totalVnd", o.order_status AS "orderStatus",
                   o.confirmation_state AS "confirmationState",
                   EXISTS (SELECT 1 FROM payment_event_inbox e WHERE e.order_id = o.order_id AND e.outcome = 'APPLIED'
                           AND e.payment_method = o.payment_method AND e.amount_vnd = o.accepted_total_vnd
                           AND e.customer_id IS NOT DISTINCT FROM o.customer_id) AS "paid",
                   to_char(o.placed_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"') AS "placedAt"
            FROM customer_order o LEFT JOIN order_address_snapshot a ON a.order_id = o.order_id
            ORDER BY o.placed_at DESC, o.order_id DESC LIMIT 5
            """, nativeQuery = true)
    List<RecentOrderRow> recentOrders();
}
