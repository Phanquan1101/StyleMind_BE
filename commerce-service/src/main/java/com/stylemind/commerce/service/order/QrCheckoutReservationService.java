package com.stylemind.commerce.service.order;

import com.stylemind.commerce.errors.ApiOperationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Local transaction boundary for a QR checkout and its held variant quantities. */
@Service
public class QrCheckoutReservationService {
    private final JdbcClient jdbc;
    private final VariantQuantityService quantityService;
    public QrCheckoutReservationService(JdbcClient jdbc, VariantQuantityService quantityService) {
        this.jdbc = jdbc;
        this.quantityService = quantityService;
    }
    public record Item(UUID variantId, int quantity, long unitPriceVnd, String productSnapshot) { }
    private record Checkout(UUID customer, String guestHash, String status) { }
    private record Reservation(UUID variantId, int quantity) { }
    public record PaymentContext(UUID checkoutId, UUID customerId, long totalVnd, String status,
            OffsetDateTime expiresAt) { }

    @Transactional(readOnly = true)
    public PaymentContext paymentContext(UUID checkoutId, UUID customerId) {
        return paymentContext(checkoutId, customerId, null);
    }

    @Transactional(readOnly = true)
    public PaymentContext paymentContext(UUID checkoutId, UUID customerId, String guestHash) {
        if ((customerId == null) == (guestHash == null)) throw notFound();
        return jdbc.sql("""
                SELECT checkout_id,customer_id,total_vnd,checkout_status,expires_at
                FROM qr_checkout WHERE checkout_id=:id AND
                    ((CAST(:customer AS uuid) IS NOT NULL AND customer_id=CAST(:customer AS uuid)) OR
                     (CAST(:guest AS text) IS NOT NULL AND guest_session_hash=CAST(:guest AS text)))
                """).param("id", checkoutId).param("customer", customerId).param("guest", guestHash)
                .query((rs, row) -> new PaymentContext(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getLong(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class)))
                .optional().orElseThrow(this::notFound);
    }

    @Transactional(readOnly = true)
    public PaymentContext recoverPaymentContext(UUID customerId, String checkoutKey) {
        UUID id = jdbc.sql("SELECT checkout_id FROM qr_checkout WHERE customer_id=:customer AND checkout_key=:key")
                .param("customer", customerId).param("key", checkoutKey).query(UUID.class).optional().orElseThrow(this::notFound);
        return paymentContext(id, customerId);
    }

    @Transactional(readOnly = true)
    public PaymentContext recoverGuestPaymentContext(String guestHash, String checkoutKey) {
        UUID id = jdbc.sql("SELECT checkout_id FROM qr_checkout WHERE guest_session_hash=:guest AND checkout_key=:key")
                .param("guest", guestHash).param("key", checkoutKey).query(UUID.class).optional().orElseThrow(this::notFound);
        return paymentContext(id, null, guestHash);
    }

    /** Stores the trusted Payment response even if cancellation raced with the network request. */
    @Transactional
    public boolean recordPaymentAttempt(UUID checkoutId, UUID customerId,
            com.stylemind.commerce.client.payment.PaymentAttemptResponse attempt, OffsetDateTime now) {
        return recordPaymentAttempt(checkoutId, customerId, null, attempt, now);
    }

    @Transactional
    public boolean recordPaymentAttempt(UUID checkoutId, UUID customerId, String guestHash,
            com.stylemind.commerce.client.payment.PaymentAttemptResponse attempt, OffsetDateTime now) {
        if ((customerId == null) == (guestHash == null)) throw notFound();
        var context = jdbc.sql("""
                SELECT checkout_id,customer_id,total_vnd,checkout_status,expires_at
                FROM qr_checkout WHERE checkout_id=:id AND
                    ((CAST(:customer AS uuid) IS NOT NULL AND customer_id=CAST(:customer AS uuid)) OR
                     (CAST(:guest AS text) IS NOT NULL AND guest_session_hash=CAST(:guest AS text))) FOR UPDATE
                """).param("id", checkoutId).param("customer", customerId).param("guest", guestHash)
                .query((rs, row) -> new PaymentContext(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                        rs.getLong(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class)))
                .optional().orElseThrow(this::notFound);
        if (attempt == null || attempt.id() == null || attempt.reference() == null || attempt.reference().isBlank()
                || !checkoutId.equals(attempt.orderId()) || !"SEPAY_VIETQR".equals(attempt.method())
                || context.totalVnd() != attempt.amountVnd()) {
            throw new ApiOperationException(HttpStatus.BAD_GATEWAY, "PAYMENT_DELIVERY_UNKNOWN", "Payment response does not match checkout");
        }
        int updated = jdbc.sql("""
                UPDATE qr_checkout SET shipping_snapshot=jsonb_set(shipping_snapshot, '{paymentAttempt}',
                    jsonb_build_object('id', CAST(:attempt AS text), 'reference', CAST(:reference AS text)), true),
                    checkout_status=CASE WHEN checkout_status='PREPARING' AND expires_at>:now
                        THEN 'AWAITING_PAYMENT' ELSE checkout_status END, updated_at=:now
                WHERE checkout_id=:id AND (shipping_snapshot->'paymentAttempt' IS NULL OR
                    (shipping_snapshot->'paymentAttempt'->>'id'=CAST(:attempt AS text)
                     AND shipping_snapshot->'paymentAttempt'->>'reference'=:reference))
                """).param("attempt", attempt.id().toString()).param("reference", attempt.reference())
                .param("now", now).param("id", checkoutId).update();
        if (updated != 1) throw new ApiOperationException(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_CONFLICT", "Payment attempt changed");
        return List.of("PREPARING", "AWAITING_PAYMENT").contains(context.status())
                && now.isBefore(context.expiresAt()) && "PENDING".equals(attempt.status())
                && attempt.expiresAt() != null && now.toInstant().isBefore(attempt.expiresAt());
    }

    /** Caller must verify the authenticated Payment event and its recorded attempt first. */
    @Transactional
    public boolean createPaidOrder(UUID checkoutId, OffsetDateTime now) {
        var context = jdbc.sql("""
                SELECT checkout_id,customer_id,total_vnd,checkout_status,expires_at
                FROM qr_checkout WHERE checkout_id=:id FOR UPDATE
                """).param("id", checkoutId).query((rs, row) -> new PaymentContext(rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class), rs.getLong(3), rs.getString(4), rs.getObject(5, OffsetDateTime.class)))
                .optional().orElseThrow(this::notFound);
        if (!List.of("PREPARING", "AWAITING_PAYMENT").contains(context.status())) return false;
        if (!now.isBefore(context.expiresAt())) {
            release(checkoutId, "EXPIRED", now);
            return false;
        }
        var items = jdbc.sql("SELECT variant_id,quantity FROM qr_checkout_item WHERE checkout_id=:id AND reservation_status='ACTIVE' ORDER BY variant_id FOR UPDATE")
                .param("id", checkoutId).query((rs, row) -> new Reservation(rs.getObject(1, UUID.class), rs.getInt(2))).list();
        if (items.isEmpty()) throw new IllegalStateException("Checkout has no reserved items");
        String number = "SM-" + checkoutId.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        jdbc.sql("""
                INSERT INTO customer_order(order_id,order_number,customer_id,guest_session_hash,checkout_key,
                    order_status,payment_method,accepted_total_vnd,placed_at,confirmation_state)
                SELECT checkout_id,:number,customer_id,guest_session_hash,checkout_key,'PAID',
                    'SEPAY_VIETQR',total_vnd,:now,'UNCONFIRMED' FROM qr_checkout WHERE checkout_id=:id
                """).param("id", checkoutId).param("number", number).param("now", now).update();
        jdbc.sql("""
                INSERT INTO order_address_snapshot(order_id,source_address_id,recipient_name,recipient_phone,address_line)
                SELECT checkout_id,CAST(shipping_snapshot->>'sourceAddressId' AS uuid),
                    shipping_snapshot->>'recipientName',shipping_snapshot->>'recipientPhone',shipping_snapshot->>'addressLine'
                FROM qr_checkout WHERE checkout_id=:id
                """).param("id", checkoutId).update();
        for (var item : items) {
            jdbc.sql("""
                    INSERT INTO order_item(order_item_id,order_id,variant_id,sku_snapshot,product_name_snapshot,
                        brand_name_snapshot,color_snapshot,size_snapshot,material_snapshot,quantity,unit_price_vnd)
                    SELECT :item,checkout_id,variant_id,product_snapshot->>'sku',product_snapshot->>'productName',
                        product_snapshot->>'brandName',product_snapshot->>'color',product_snapshot->>'size',
                        product_snapshot->>'material',quantity,unit_price_vnd
                    FROM qr_checkout_item WHERE checkout_id=:id AND variant_id=:variant
                    """).param("item", UUID.randomUUID()).param("id", checkoutId).param("variant", item.variantId()).update();
        }
        jdbc.sql("""
                DELETE FROM cart_item c USING qr_checkout_item q
                WHERE q.checkout_id=:id AND c.cart_item_id=CAST(q.product_snapshot->>'cartItemId' AS uuid)
                    AND c.variant_id=q.variant_id AND c.quantity=q.quantity
                """).param("id", checkoutId).update();
        jdbc.sql("UPDATE qr_checkout_item SET reservation_status='CONSUMED' WHERE checkout_id=:id AND reservation_status='ACTIVE'")
                .param("id", checkoutId).update();
        jdbc.sql("""
                INSERT INTO order_status_history(history_id,order_id,from_status,to_status,actor_user_id,changed_at,reason)
                VALUES (:history,:id,NULL,'PAID',NULL,:now,'Payment confirmed before order creation')
                """).param("history", UUID.randomUUID()).param("id", checkoutId).param("now", now).update();
        jdbc.sql("UPDATE qr_checkout SET checkout_status='ORDER_CREATED',order_id=:id,updated_at=:now WHERE checkout_id=:id")
                .param("id", checkoutId).param("now", now).update();
        return true;
    }

    public void lockCheckoutKey(UUID customerId, String guestHash, String key) {
        String owner = customerId != null ? "customer:" + customerId : "guest:" + guestHash;
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:value, 0))")
                .param("value", owner + ":" + key).query((rs, row) -> true).single();
    }

    public boolean checkoutKeyExists(UUID customerId, String guestHash, String key) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM qr_checkout WHERE checkout_key=:key
                    AND ((CAST(:customer AS uuid) IS NOT NULL AND customer_id=CAST(:customer AS uuid))
                      OR (CAST(:guest AS text) IS NOT NULL AND guest_session_hash=CAST(:guest AS text))))
                """).param("key", key).param("customer", customerId).param("guest", guestHash)
                .query(Boolean.class).single();
    }

    @Transactional
    public void reserve(UUID checkoutId, UUID customerId, String guestHash, String key, long total,
            String shippingSnapshot, List<Item> items, OffsetDateTime now) {
        if (checkoutId == null || (customerId == null) == (guestHash == null) || key == null || key.isBlank()
                || total <= 0 || items == null || items.isEmpty() || now == null
                || items.stream().anyMatch(item -> item.variantId() == null || item.quantity() <= 0 || item.unitPriceVnd() <= 0)) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid checkout");
        }
        var sorted = items.stream().sorted(java.util.Comparator.comparing(Item::variantId)).toList();
        if (sorted.stream().map(Item::variantId).distinct().count() != sorted.size()
                || sorted.stream().mapToLong(item -> Math.multiplyExact(item.unitPriceVnd(), item.quantity())).sum() != total) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid checkout items");
        }
        jdbc.sql("""
                INSERT INTO qr_checkout(checkout_id,customer_id,guest_session_hash,checkout_key,checkout_status,total_vnd,
                    shipping_snapshot,payment_reference_id,created_at,expires_at,updated_at)
                VALUES (:id,:customer,:guest,:key,'PREPARING',:total,CAST(:shipping AS jsonb),:id,:now,:expires,:now)
                """).param("id", checkoutId).param("customer", customerId).param("guest", guestHash)
                .param("key", key).param("total", total).param("shipping", shippingSnapshot)
                .param("now", now).param("expires", now.plusMinutes(15)).update();
        var requested = sorted.stream().collect(java.util.stream.Collectors.toMap(Item::variantId, Item::quantity));
        if (!quantityService.allocate(requested)) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "INSUFFICIENT_STOCK", "Requested quantity is unavailable");
        }
        for (var item : sorted) {
            jdbc.sql("""
                    INSERT INTO qr_checkout_item(checkout_id,variant_id,quantity,unit_price_vnd,product_snapshot,reservation_status)
                    VALUES (:checkout,:variant,:quantity,:price,CAST(:snapshot AS jsonb),'ACTIVE')
                    """).param("checkout", checkoutId).param("variant", item.variantId()).param("quantity", item.quantity())
                    .param("price", item.unitPriceVnd()).param("snapshot", item.productSnapshot()).update();
        }
    }

    @Transactional
    public void cancel(UUID checkoutId, UUID customerId, String guestHash, OffsetDateTime now) {
        var checkout = jdbc.sql("SELECT customer_id,guest_session_hash,checkout_status FROM qr_checkout WHERE checkout_id=:id FOR UPDATE")
                .param("id", checkoutId).query((rs, row) -> new Checkout(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)))
                .optional().orElseThrow(this::notFound);
        if ((customerId == null && guestHash == null) || !java.util.Objects.equals(customerId, checkout.customer())
                || !java.util.Objects.equals(guestHash, checkout.guestHash())) throw notFound();
        if ("CANCELLED".equals(checkout.status())) return;
        if (!List.of("PREPARING", "AWAITING_PAYMENT").contains(checkout.status())) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "CHECKOUT_NOT_CANCELLABLE", "Checkout cannot be cancelled");
        }
        release(checkoutId, "CANCELLED", now);
    }

    public List<UUID> expiredCheckoutIds(OffsetDateTime now) {
        return jdbc.sql("""
                SELECT checkout_id FROM qr_checkout
                WHERE checkout_status IN ('PREPARING','AWAITING_PAYMENT') AND expires_at<=:now
                ORDER BY expires_at,checkout_id LIMIT 100
                """).param("now", now).query(UUID.class).list();
    }

    /** The caller restores order quantities while holding the order lock in the same transaction. */
    @Transactional
    public void releaseCancelledOrderItems(UUID orderId) {
        jdbc.sql("UPDATE qr_checkout_item SET reservation_status='RELEASED' WHERE checkout_id=:id AND reservation_status='CONSUMED'")
                .param("id", orderId).update();
    }

    @Transactional
    public void expire(UUID checkoutId, OffsetDateTime now) {
        boolean eligible = jdbc.sql("""
                SELECT checkout_status IN ('PREPARING','AWAITING_PAYMENT') AND expires_at<=:now
                FROM qr_checkout WHERE checkout_id=:id FOR UPDATE
                """).param("now", now).param("id", checkoutId).query(Boolean.class).optional().orElse(false);
        if (eligible) release(checkoutId, "EXPIRED", now);
    }

    private void release(UUID checkoutId, String status, OffsetDateTime now) {
        var items = jdbc.sql("SELECT variant_id,quantity FROM qr_checkout_item WHERE checkout_id=:id AND reservation_status='ACTIVE' ORDER BY variant_id FOR UPDATE")
                .param("id", checkoutId).query((rs, row) -> new Reservation(rs.getObject(1, UUID.class), rs.getInt(2))).list();
        quantityService.restore(items.stream().collect(java.util.stream.Collectors.toMap(Reservation::variantId, Reservation::quantity)));
        jdbc.sql("UPDATE qr_checkout_item SET reservation_status='RELEASED' WHERE checkout_id=:id AND reservation_status='ACTIVE'")
                .param("id", checkoutId).update();
        jdbc.sql("UPDATE qr_checkout SET checkout_status=:status,updated_at=:now WHERE checkout_id=:id")
                .param("status", status).param("now", now).param("id", checkoutId).update();
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Checkout was not found");
    }
}
