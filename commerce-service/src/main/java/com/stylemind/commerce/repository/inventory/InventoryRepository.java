package com.stylemind.commerce.repository.inventory;

import com.stylemind.commerce.enums.PaymentMethod;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
    private static final int EXPIRY_BATCH_SIZE = 100;
    private final JdbcClient jdbc;

    public InventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Stock(UUID variantId, int onHand, int reserved) {
        public int available() { return onHand - reserved; }
    }

    public record Reservation(UUID reservationId, UUID variantId, int quantity, String status) { }
    public record LockedOrder(String paymentMethod, String orderStatus) { }

    public void initialize(UUID variantId, int onHand, OffsetDateTime now) {
        jdbc.sql("""
                INSERT INTO public.variant_inventory(variant_id,on_hand_quantity,reserved_quantity,updated_at)
                VALUES (:id,:quantity,0,:now)
                ON CONFLICT (variant_id) DO NOTHING
                """).param("id", variantId).param("quantity", onHand).param("now", now).update();
    }

    public Map<UUID, Integer> available(Collection<UUID> variantIds) {
        if (variantIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT variant_id,on_hand_quantity-reserved_quantity AS available_quantity
                FROM public.variant_inventory WHERE variant_id IN (:ids)
                """).param("ids", variantIds).query((rs, row) -> {
                    result.put(rs.getObject("variant_id", UUID.class), rs.getInt("available_quantity"));
                    return true;
                }).list();
        return result;
    }

    public boolean reserveStock(UUID variantId, int quantity, OffsetDateTime now) {
        return jdbc.sql("""
                UPDATE public.variant_inventory SET reserved_quantity=reserved_quantity+:quantity,updated_at=:now
                WHERE variant_id=:id AND on_hand_quantity-reserved_quantity>=:quantity
                """).param("id", variantId).param("quantity", quantity).param("now", now).update() == 1;
    }

    public void releaseReservedStock(UUID variantId, int quantity, OffsetDateTime now) {
        int changed = jdbc.sql("""
                UPDATE public.variant_inventory SET reserved_quantity=reserved_quantity-:quantity,updated_at=:now
                WHERE variant_id=:id AND reserved_quantity>=:quantity
                """).param("id", variantId).param("quantity", quantity).param("now", now).update();
        requireInventoryUpdate(changed);
    }

    public void consumeCheckoutReservation(UUID orderId, UUID variantId, int quantity, OffsetDateTime now) {
        int changed = jdbc.sql("""
                UPDATE public.variant_inventory
                SET on_hand_quantity=on_hand_quantity-:quantity,
                    reserved_quantity=reserved_quantity-:quantity,updated_at=:now
                WHERE variant_id=:id AND reserved_quantity>=:quantity AND on_hand_quantity>=:quantity
                """).param("id", variantId).param("quantity", quantity).param("now", now).update();
        requireInventoryUpdate(changed);
        jdbc.sql("""
                INSERT INTO public.inventory_reservation(reservation_id,order_id,variant_id,quantity,
                    reservation_status,expires_at,created_at,updated_at)
                VALUES (:reservation,:order,:variant,:quantity,'CONSUMED',NULL,:now,:now)
                """).param("reservation", UUID.randomUUID()).param("order", orderId)
                .param("variant", variantId).param("quantity", quantity).param("now", now).update();
    }

    public boolean allocate(UUID orderId, PaymentMethod method, Map<UUID, Integer> requested, OffsetDateTime now) {
        List<UUID> ids = requested.keySet().stream().sorted().toList();
        Map<UUID, Stock> locked = lockStocks(ids);
        if (locked.size() != ids.size()
                || ids.stream().anyMatch(id -> requested.get(id) > locked.get(id).available())) {
            return false;
        }
        OffsetDateTime expiresAt = method == PaymentMethod.SEPAY_VIETQR ? now.plusMinutes(15) : null;
        for (UUID variantId : ids) {
            int quantity = requested.get(variantId);
            if (method == PaymentMethod.SEPAY_VIETQR) {
                if (!reserveStock(variantId, quantity, now)) {
                    throw new IllegalStateException("Locked inventory could not be reserved");
                }
            } else {
                jdbc.sql("""
                        UPDATE public.variant_inventory
                        SET on_hand_quantity=on_hand_quantity-:quantity,updated_at=:now
                        WHERE variant_id=:id
                        """).param("quantity", quantity).param("now", now).param("id", variantId).update();
            }
            jdbc.sql("""
                    INSERT INTO public.inventory_reservation(
                        reservation_id,order_id,variant_id,quantity,reservation_status,expires_at,created_at,updated_at)
                    VALUES (:reservation,:order,:variant,:quantity,:status,:expires,:now,:now)
                    """).param("reservation", UUID.randomUUID()).param("order", orderId)
                    .param("variant", variantId).param("quantity", quantity)
                    .param("status", method == PaymentMethod.SEPAY_VIETQR ? "ACTIVE" : "CONSUMED")
                    .param("expires", expiresAt).param("now", now).update();
        }
        return true;
    }

    public void consumeSepay(UUID orderId, OffsetDateTime now) {
        for (Reservation reservation : lockReservations(orderId)) {
            if (!"ACTIVE".equals(reservation.status())) {
                continue;
            }
            int changed = jdbc.sql("""
                    UPDATE public.variant_inventory
                    SET on_hand_quantity=on_hand_quantity-:quantity,
                        reserved_quantity=reserved_quantity-:quantity,updated_at=:now
                    WHERE variant_id=:variant AND reserved_quantity>=:quantity
                        AND on_hand_quantity>=:quantity
                    """).param("quantity", reservation.quantity()).param("now", now)
                    .param("variant", reservation.variantId()).update();
            if (changed != 1) {
                throw new IllegalStateException("Reserved inventory could not be consumed");
            }
            setReservationStatus(reservation.reservationId(), "ACTIVE", "CONSUMED", now);
        }
    }

    public void releaseForCancellation(UUID orderId, OffsetDateTime now) {
        for (Reservation reservation : lockReservations(orderId)) {
            if ("RELEASED".equals(reservation.status())) {
                continue;
            }
            if ("ACTIVE".equals(reservation.status())) {
                releaseReservedStock(reservation.variantId(), reservation.quantity(), now);
            } else {
                int changed = jdbc.sql("""
                        UPDATE public.variant_inventory
                        SET on_hand_quantity=on_hand_quantity+:quantity,updated_at=:now
                        WHERE variant_id=:variant
                        """).param("quantity", reservation.quantity()).param("now", now)
                        .param("variant", reservation.variantId()).update();
                requireInventoryUpdate(changed);
            }
            setReservationStatus(reservation.reservationId(), reservation.status(), "RELEASED", now);
        }
        jdbc.sql("""
                UPDATE public.qr_checkout_item SET reservation_status='RELEASED'
                WHERE checkout_id=:id AND reservation_status='CONSUMED'
                """).param("id", orderId).update();
    }

    public List<UUID> expiredOrderIds(OffsetDateTime now) {
        return jdbc.sql("""
                SELECT DISTINCT order_id FROM public.inventory_reservation
                WHERE reservation_status='ACTIVE' AND expires_at<=:now
                ORDER BY order_id LIMIT :limit
                """).param("now", now).param("limit", EXPIRY_BATCH_SIZE)
                .query(UUID.class).list();
    }

    public Optional<LockedOrder> lockOrder(UUID orderId) {
        return jdbc.sql("""
                SELECT payment_method,order_status FROM public.customer_order
                WHERE order_id=:id FOR UPDATE
                """).param("id", orderId).query((rs, row) ->
                new LockedOrder(rs.getString("payment_method"), rs.getString("order_status"))).optional();
    }

    public boolean cancelExpiredOrder(UUID orderId, OffsetDateTime now) {
        int changed = jdbc.sql("""
                UPDATE public.customer_order SET order_status='CANCELLED'
                WHERE order_id=:id AND order_status='PENDING_PAYMENT' AND payment_method='SEPAY_VIETQR'
                """).param("id", orderId).update();
        if (changed != 1) {
            return false;
        }
        jdbc.sql("""
                INSERT INTO public.order_status_history(
                    history_id,order_id,from_status,to_status,actor_user_id,changed_at,reason)
                VALUES (:history,:order,'PENDING_PAYMENT','CANCELLED',NULL,:now,
                    'SePay inventory reservation expired')
                """).param("history", UUID.randomUUID()).param("order", orderId).param("now", now).update();
        return true;
    }

    private Map<UUID, Stock> lockStocks(Collection<UUID> variantIds) {
        Map<UUID, Stock> result = new LinkedHashMap<>();
        jdbc.sql("""
                SELECT variant_id,on_hand_quantity,reserved_quantity
                FROM public.variant_inventory WHERE variant_id IN (:ids)
                ORDER BY variant_id FOR UPDATE
                """).param("ids", variantIds).query((rs, row) -> {
                    Stock stock = new Stock(rs.getObject("variant_id", UUID.class),
                            rs.getInt("on_hand_quantity"), rs.getInt("reserved_quantity"));
                    result.put(stock.variantId(), stock);
                    return true;
                }).list();
        return result;
    }

    private List<Reservation> lockReservations(UUID orderId) {
        return jdbc.sql("""
                SELECT reservation_id,variant_id,quantity,reservation_status
                FROM public.inventory_reservation WHERE order_id=:order
                ORDER BY variant_id FOR UPDATE
                """).param("order", orderId).query((rs, row) -> new Reservation(
                        rs.getObject("reservation_id", UUID.class),
                        rs.getObject("variant_id", UUID.class), rs.getInt("quantity"),
                        rs.getString("reservation_status"))).list();
    }

    private void setReservationStatus(UUID reservationId, String from, String to, OffsetDateTime now) {
        int changed = jdbc.sql("""
                UPDATE public.inventory_reservation SET reservation_status=:to,updated_at=:now
                WHERE reservation_id=:id AND reservation_status=:from
                """).param("to", to).param("now", now).param("id", reservationId).param("from", from).update();
        if (changed != 1) {
            throw new IllegalStateException("Inventory reservation changed concurrently");
        }
    }

    private void requireInventoryUpdate(int changed) {
        if (changed != 1) {
            throw new IllegalStateException("Inventory reservation could not be released");
        }
    }
}
