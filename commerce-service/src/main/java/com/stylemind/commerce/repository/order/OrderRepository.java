package com.stylemind.commerce.repository.order;

import com.stylemind.commerce.model.cart.CartItem;
import com.stylemind.commerce.model.order.AddressSnapshot;
import com.stylemind.commerce.model.order.CancellationRequest;
import com.stylemind.commerce.model.order.CancellationStatus;
import com.stylemind.commerce.model.order.OrderDetail;
import com.stylemind.commerce.model.order.OrderItem;
import com.stylemind.commerce.model.order.OrderStatus;
import com.stylemind.commerce.model.order.OrderStatusHistoryEntry;
import com.stylemind.commerce.model.order.OrderSummary;
import com.stylemind.commerce.model.order.PaymentMethod;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class OrderRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public void lockCart(UUID cartId) {
        jdbc.queryForList("SELECT cart_id FROM public.cart WHERE cart_id = :cartId FOR UPDATE",
                new MapSqlParameterSource("cartId", cartId));
    }

    public OrderSummary insertOrder(UUID orderId, String orderNumber, UUID customerId, String checkoutKey,
            PaymentMethod paymentMethod, long total, OffsetDateTime now) {
        OrderStatus status = paymentMethod == PaymentMethod.COD ? OrderStatus.PLACED : OrderStatus.PENDING_PAYMENT;
        jdbc.update("""
                INSERT INTO public.customer_order (
                    order_id, order_number, customer_id, checkout_key, order_status, payment_method,
                    accepted_total_vnd, placed_at
                ) VALUES (:orderId, :orderNumber, :customerId, :checkoutKey, :status, :paymentMethod, :total, :now)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("orderNumber", orderNumber, Types.VARCHAR)
                .addValue("customerId", customerId)
                .addValue("checkoutKey", checkoutKey, Types.VARCHAR)
                .addValue("status", status.name(), Types.VARCHAR)
                .addValue("paymentMethod", paymentMethod.name(), Types.VARCHAR)
                .addValue("total", total)
                .addValue("now", now));
        insertHistory(orderId, null, status.name(), customerId, now, "Order created");
        return findSummary(orderId).orElseThrow();
    }

    public void insertAddress(UUID orderId, AddressSnapshot address) {
        jdbc.update("""
                INSERT INTO public.order_address_snapshot (
                    order_id, source_address_id, recipient_name, recipient_phone, address_line
                ) VALUES (:orderId, :sourceAddressId, :recipientName, :recipientPhone, :addressLine)
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("sourceAddressId", address.sourceAddressId())
                .addValue("recipientName", address.recipientName(), Types.VARCHAR)
                .addValue("recipientPhone", address.recipientPhone(), Types.VARCHAR)
                .addValue("addressLine", address.addressLine(), Types.VARCHAR));
    }

    public void insertItems(UUID orderId, List<CartItem> items) {
        for (CartItem item : items) {
            jdbc.update("""
                    INSERT INTO public.order_item (
                        order_item_id, order_id, variant_id, sku_snapshot, product_name_snapshot,
                        brand_name_snapshot, color_snapshot, size_snapshot, material_snapshot,
                        image_url_snapshot, quantity, unit_price_vnd
                    ) VALUES (
                        :orderItemId, :orderId, :variantId, :sku, :productName, :brandName, :color, :size,
                        :material, null, :quantity, :unitPrice
                    )
                    """, new MapSqlParameterSource()
                    .addValue("orderItemId", UUID.randomUUID())
                    .addValue("orderId", orderId)
                    .addValue("variantId", item.variantId())
                    .addValue("sku", item.sku(), Types.VARCHAR)
                    .addValue("productName", item.productName(), Types.VARCHAR)
                    .addValue("brandName", item.brandName(), Types.VARCHAR)
                    .addValue("color", item.color(), Types.VARCHAR)
                    .addValue("size", item.size(), Types.VARCHAR)
                    .addValue("material", item.material(), Types.VARCHAR)
                    .addValue("quantity", item.quantity())
                    .addValue("unitPrice", item.unitPriceVnd()));
        }
    }

    public void markCartCheckedOut(UUID cartId, OffsetDateTime now) {
        jdbc.update("UPDATE public.cart SET cart_status = 'CHECKED_OUT', updated_at = :now WHERE cart_id = :cartId",
                new MapSqlParameterSource("cartId", cartId).addValue("now", now));
    }

    public Optional<OrderSummary> findByCheckoutKey(UUID customerId, String checkoutKey) {
        return jdbc.query("""
                SELECT * FROM public.customer_order WHERE customer_id = :customerId AND checkout_key = :checkoutKey
                """, new MapSqlParameterSource("customerId", customerId).addValue("checkoutKey", checkoutKey),
                rs -> rs.next() ? Optional.of(summary(rs)) : Optional.empty());
    }

    public Optional<OrderSummary> findSummary(UUID orderId) {
        return jdbc.query("SELECT * FROM public.customer_order WHERE order_id = :orderId",
                new MapSqlParameterSource("orderId", orderId),
                rs -> rs.next() ? Optional.of(summary(rs)) : Optional.empty());
    }

    public long count(UUID customerId, OrderStatus status) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM public.customer_order
                WHERE (CAST(:customerId AS uuid) IS NULL OR customer_id = CAST(:customerId AS uuid))
                  AND (CAST(:status AS text) IS NULL OR order_status = CAST(:status AS text))
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("status", status == null ? null : status.name(), Types.VARCHAR), Long.class);
    }

    public List<OrderSummary> list(UUID customerId, OrderStatus status, int size, long offset) {
        return jdbc.query("""
                SELECT * FROM public.customer_order
                WHERE (CAST(:customerId AS uuid) IS NULL OR customer_id = CAST(:customerId AS uuid))
                  AND (CAST(:status AS text) IS NULL OR order_status = CAST(:status AS text))
                ORDER BY placed_at DESC, order_id DESC LIMIT :size OFFSET :offset
                """, new MapSqlParameterSource()
                .addValue("customerId", customerId)
                .addValue("status", status == null ? null : status.name(), Types.VARCHAR)
                .addValue("size", size)
                .addValue("offset", offset), (rs, row) -> summary(rs));
    }

    public OrderDetail detail(OrderSummary summary) {
        return new OrderDetail(summary, address(summary.orderId()).orElseThrow(), items(summary.orderId()),
                history(summary.orderId()), cancellation(summary.orderId()).orElse(null));
    }

    public Optional<AddressSnapshot> address(UUID orderId) {
        return jdbc.query("""
                SELECT source_address_id, recipient_name, recipient_phone, address_line
                FROM public.order_address_snapshot WHERE order_id = :orderId
                """, new MapSqlParameterSource("orderId", orderId), rs -> rs.next()
                ? Optional.of(new AddressSnapshot(rs.getObject("source_address_id", UUID.class),
                rs.getString("recipient_name"), rs.getString("recipient_phone"), rs.getString("address_line")))
                : Optional.empty());
    }

    public List<OrderItem> items(UUID orderId) {
        return jdbc.query("""
                SELECT *, quantity::bigint * unit_price_vnd AS line_total_vnd
                FROM public.order_item WHERE order_id = :orderId
                ORDER BY order_item_id ASC
                """, new MapSqlParameterSource("orderId", orderId), (rs, row) -> orderItem(rs));
    }

    public List<OrderStatusHistoryEntry> history(UUID orderId) {
        return jdbc.query("""
                SELECT * FROM public.order_status_history WHERE order_id = :orderId
                ORDER BY changed_at ASC, history_id ASC
                """, new MapSqlParameterSource("orderId", orderId), (rs, row) -> history(rs));
    }

    public Optional<CancellationRequest> cancellation(UUID orderId) {
        return jdbc.query("SELECT * FROM public.cancellation_request WHERE order_id = :orderId",
                new MapSqlParameterSource("orderId", orderId),
                rs -> rs.next() ? Optional.of(cancellation(rs)) : Optional.empty());
    }

    public int updateStatus(UUID orderId, OrderStatus from, OrderStatus to, UUID actorId, String reason,
            OffsetDateTime now) {
        int rows = jdbc.update("""
                UPDATE public.customer_order
                SET order_status = :toStatus, completed_at = CASE WHEN :toStatus = 'COMPLETED' THEN :now ELSE completed_at END
                WHERE order_id = :orderId AND order_status = :fromStatus
                """, new MapSqlParameterSource()
                .addValue("orderId", orderId)
                .addValue("fromStatus", from.name(), Types.VARCHAR)
                .addValue("toStatus", to.name(), Types.VARCHAR)
                .addValue("now", now));
        if (rows == 1) {
            insertHistory(orderId, from.name(), to.name(), actorId, now, reason);
        }
        return rows;
    }

    public CancellationRequest insertCancellation(UUID requestId, UUID orderId, UUID requestedBy, String reason,
            OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO public.cancellation_request (
                    cancellation_request_id, order_id, requested_by_user_id, reason, request_status, requested_at
                ) VALUES (:requestId, :orderId, :requestedBy, :reason, 'PENDING', :now)
                """, new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("orderId", orderId)
                .addValue("requestedBy", requestedBy)
                .addValue("reason", reason, Types.VARCHAR)
                .addValue("now", now));
        return cancellation(orderId).orElseThrow();
    }

    public int reviewCancellation(UUID requestId, CancellationStatus status, UUID reviewerId, String decisionReason,
            OffsetDateTime now) {
        return jdbc.update("""
                UPDATE public.cancellation_request
                SET request_status = :status, reviewed_by_user_id = :reviewerId,
                    reviewed_at = :now, decision_reason = :decisionReason
                WHERE cancellation_request_id = :requestId AND request_status = 'PENDING'
                """, new MapSqlParameterSource()
                .addValue("requestId", requestId)
                .addValue("status", status.name(), Types.VARCHAR)
                .addValue("reviewerId", reviewerId)
                .addValue("now", now)
                .addValue("decisionReason", decisionReason, Types.VARCHAR));
    }

    public Optional<CancellationRequest> cancellationById(UUID requestId) {
        return jdbc.query("SELECT * FROM public.cancellation_request WHERE cancellation_request_id = :requestId",
                new MapSqlParameterSource("requestId", requestId),
                rs -> rs.next() ? Optional.of(cancellation(rs)) : Optional.empty());
    }

    private void insertHistory(UUID orderId, String from, String to, UUID actor, OffsetDateTime now, String reason) {
        jdbc.update("""
                INSERT INTO public.order_status_history (
                    history_id, order_id, from_status, to_status, actor_user_id, changed_at, reason
                ) VALUES (:historyId, :orderId, :fromStatus, :toStatus, :actorId, :now, :reason)
                """, new MapSqlParameterSource()
                .addValue("historyId", UUID.randomUUID())
                .addValue("orderId", orderId)
                .addValue("fromStatus", from, Types.VARCHAR)
                .addValue("toStatus", to, Types.VARCHAR)
                .addValue("actorId", actor)
                .addValue("now", now)
                .addValue("reason", reason, Types.VARCHAR));
    }

    private OrderSummary summary(ResultSet rs) throws SQLException {
        return new OrderSummary(rs.getObject("order_id", UUID.class), rs.getString("order_number"),
                rs.getObject("customer_id", UUID.class), rs.getString("checkout_key"),
                OrderStatus.valueOf(rs.getString("order_status")),
                PaymentMethod.valueOf(rs.getString("payment_method")), rs.getLong("accepted_total_vnd"),
                rs.getObject("placed_at", OffsetDateTime.class), rs.getObject("completed_at", OffsetDateTime.class));
    }

    private OrderItem orderItem(ResultSet rs) throws SQLException {
        return new OrderItem(rs.getObject("order_item_id", UUID.class), rs.getObject("variant_id", UUID.class),
                rs.getString("sku_snapshot"), rs.getString("product_name_snapshot"), rs.getString("brand_name_snapshot"),
                rs.getString("color_snapshot"), rs.getString("size_snapshot"), rs.getString("material_snapshot"),
                rs.getString("image_url_snapshot"), rs.getInt("quantity"), rs.getLong("unit_price_vnd"),
                rs.getLong("line_total_vnd"));
    }

    private OrderStatusHistoryEntry history(ResultSet rs) throws SQLException {
        return new OrderStatusHistoryEntry(rs.getObject("history_id", UUID.class), rs.getString("from_status"),
                rs.getString("to_status"), rs.getObject("actor_user_id", UUID.class),
                rs.getObject("changed_at", OffsetDateTime.class), rs.getString("reason"));
    }

    private CancellationRequest cancellation(ResultSet rs) throws SQLException {
        return new CancellationRequest(rs.getObject("cancellation_request_id", UUID.class),
                rs.getObject("order_id", UUID.class), rs.getObject("requested_by_user_id", UUID.class),
                rs.getString("reason"), CancellationStatus.valueOf(rs.getString("request_status")),
                rs.getObject("requested_at", OffsetDateTime.class), rs.getObject("reviewed_by_user_id", UUID.class),
                rs.getObject("reviewed_at", OffsetDateTime.class), rs.getString("decision_reason"));
    }
}
