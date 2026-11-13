package com.stylemind.commerce.repository.returns;

import com.stylemind.commerce.dto.returns.ReturnCommand.Image;
import com.stylemind.commerce.dto.returns.ReturnResponse;
import com.stylemind.commerce.dto.returns.ReturnResponse.Evidence;
import com.stylemind.commerce.dto.returns.ReturnResponse.History;
import com.stylemind.commerce.dto.returns.ReturnResponse.Item;
import com.stylemind.commerce.dto.returns.ReturnResponse.Status;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReturnRepository {

    private static final String READ_RETURNS = """
            SELECT r.return_id,r.order_id,o.order_number,r.return_status,r.reason,r.decision_reason,
                   r.amount_vnd,r.created_at
            FROM public.return_request r
            JOIN public.customer_order o ON o.order_id=r.order_id
            """;

    private final JdbcClient jdbc;

    public Optional<OwnedOrder> ownedOrder(UUID orderId, UUID owner, boolean lock) {
        return jdbc.sql("""
                SELECT o.order_status,o.completed_at,e.event_id
                FROM public.customer_order o
                LEFT JOIN public.payment_event_inbox e ON e.order_id=o.order_id AND e.outcome='APPLIED'
                    AND e.customer_id=o.customer_id AND e.payment_method=o.payment_method
                    AND e.amount_vnd=o.accepted_total_vnd AND e.payment_method IN ('COD','SEPAY_VIETQR','VNPAY')
                WHERE o.order_id=:order AND o.customer_id=:owner
                """ + (lock ? " FOR UPDATE OF o" : ""))
                .param("order", orderId).param("owner", owner)
                .query((rs, row) -> new OwnedOrder(rs.getString("order_status"),
                        rs.getObject("completed_at", OffsetDateTime.class),
                        rs.getObject("event_id", UUID.class))).optional();
    }

    public List<Item> availableItems(UUID orderId) {
        return jdbc.sql("""
                SELECT i.order_item_id,i.product_name_snapshot,i.sku_snapshot,i.color_snapshot,i.size_snapshot,i.unit_price_vnd,
                       i.quantity-COALESCE(used.quantity,0) AS quantity
                FROM public.order_item i
                LEFT JOIN (
                    SELECT ri.order_item_id,SUM(ri.quantity) AS quantity
                    FROM public.return_request_item ri
                    JOIN public.return_request r ON r.return_id=ri.return_id
                    WHERE r.order_id=:order AND r.return_status<>'REJECTED'
                    GROUP BY ri.order_item_id
                ) used ON used.order_item_id=i.order_item_id
                WHERE i.order_id=:order AND i.quantity>COALESCE(used.quantity,0)
                ORDER BY i.order_item_id
                """).param("order", orderId).query((rs, row) -> item(rs)).list();
    }

    public long remainingAmount(UUID orderId) {
        return jdbc.sql("""
                SELECT GREATEST(0, o.accepted_total_vnd-COALESCE(SUM(r.amount_vnd),0))
                FROM public.customer_order o LEFT JOIN public.return_request r
                    ON r.order_id=o.order_id AND r.return_status<>'REJECTED'
                WHERE o.order_id=:order
                GROUP BY o.accepted_total_vnd
                """).param("order", orderId).query(Long.class).single();
    }

    public Optional<ExistingRequest> existingRequest(UUID orderId, UUID requestKey) {
        return jdbc.sql("""
                SELECT return_id,request_fingerprint FROM public.return_request
                WHERE order_id=:order AND request_key=:key
                """).param("order", orderId).param("key", requestKey)
                .query((rs, row) -> new ExistingRequest(rs.getObject("return_id", UUID.class),
                        rs.getString("request_fingerprint"))).optional();
    }

    public boolean hasRequest(UUID orderId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM public.return_request WHERE order_id=:order)")
                .param("order", orderId).query(Boolean.class).single();
    }

    public void insert(UUID id, UUID orderId, UUID paymentEvent, UUID requestKey, String fingerprint,
                       String reason, long amount, OffsetDateTime now) {
        jdbc.sql("""
                INSERT INTO public.return_request(return_id,order_id,payment_event_id,request_key,request_fingerprint,
                    return_status,reason,amount_vnd,created_at)
                VALUES (:id,:order,:event,:key,:fingerprint,'REQUESTED',:reason,:amount,:now)
                """).param("id", id).param("order", orderId).param("event", paymentEvent).param("key", requestKey)
                .param("fingerprint", fingerprint).param("reason", reason).param("amount", amount).param("now", now)
                .update();
    }

    public void addItems(UUID id, List<Item> items) {
        for (Item item : items) {
            jdbc.sql("""
                    INSERT INTO public.return_request_item(return_id,order_item_id,quantity,unit_price_vnd)
                    VALUES (:id,:item,:quantity,:price)
                    """).param("id", id).param("item", item.orderItemId()).param("quantity", item.quantity())
                    .param("price", item.unitPriceVnd()).update();
        }
    }

    public void addEvidence(UUID id, List<Image> evidence, OffsetDateTime now) {
        for (Image image : evidence) {
            jdbc.sql("""
                    INSERT INTO public.return_evidence(image_id,return_id,content_type,image_bytes,uploaded_at)
                    VALUES (:image,:id,:type,:bytes,:now)
                    """).param("image", UUID.randomUUID()).param("id", id).param("type", image.contentType())
                    .param("bytes", image.bytes()).param("now", now).update();
        }
    }

    public Optional<Image> evidence(UUID id, UUID imageId, UUID actor, boolean admin) {
        return jdbc.sql("""
                SELECT e.content_type,e.image_bytes
                FROM public.return_evidence e
                JOIN public.return_request r ON r.return_id=e.return_id
                JOIN public.customer_order o ON o.order_id=r.order_id
                WHERE e.return_id=:id AND e.image_id=:image AND (:admin OR o.customer_id=:actor)
                """).param("id", id).param("image", imageId).param("actor", actor).param("admin", admin)
                .query((rs, row) -> new Image(rs.getString("content_type"), rs.getBytes("image_bytes"))).optional();
    }

    public Optional<Status> lockStatus(UUID id, UUID actor, boolean admin) {
        return jdbc.sql("""
                SELECT r.return_status FROM public.return_request r
                JOIN public.customer_order o ON o.order_id=r.order_id
                WHERE r.return_id=:id AND (:admin OR o.customer_id=:actor)
                FOR UPDATE OF o,r
                """).param("id", id).param("actor", actor).param("admin", admin)
                .query((rs, row) -> Status.valueOf(rs.getString("return_status"))).optional();
    }

    public void changeStatus(UUID id, Status status, String reason) {
        jdbc.sql("""
                UPDATE public.return_request SET return_status=:status,decision_reason=:reason WHERE return_id=:id
                """).param("id", id).param("status", status.name()).param("reason", reason, Types.VARCHAR).update();
    }

    public void appendHistory(UUID id, Status status, String action, UUID actor, String reason, OffsetDateTime now) {
        jdbc.sql("""
                INSERT INTO public.return_status_history(return_id,to_status,action,actor_user_id,reason,changed_at)
                VALUES (:id,:status,:action,:actor,:reason,:now)
                """).param("id", id).param("status", status.name()).param("action", action).param("actor", actor)
                .param("reason", reason, Types.VARCHAR).param("now", now).update();
    }

    public Optional<ReturnResponse> find(UUID id) {
        return detail(jdbc.sql(READ_RETURNS + "WHERE r.return_id=:id")
                .param("id", id).query(ReturnRepository::header).list()).stream().findFirst();
    }

    public List<ReturnResponse> findForOrder(UUID orderId) {
        return detail(jdbc.sql(READ_RETURNS + "WHERE r.order_id=:order ORDER BY r.created_at DESC,r.return_id DESC")
                .param("order", orderId).query(ReturnRepository::header).list());
    }

    public List<ReturnResponse> adminPage(int size, long offset) {
        return detail(jdbc.sql(READ_RETURNS + "ORDER BY r.created_at DESC,r.return_id DESC LIMIT :size OFFSET :offset")
                .param("size", size).param("offset", offset).query(ReturnRepository::header).list());
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM public.return_request").query(Long.class).single();
    }

    private List<ReturnResponse> detail(List<Header> headers) {
        if (headers.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = headers.stream().map(Header::id).toList();
        Map<UUID, List<Item>> items = grouped(jdbc.sql("""
                SELECT ri.return_id,ri.order_item_id,i.product_name_snapshot,i.sku_snapshot,i.color_snapshot,i.size_snapshot,
                       ri.quantity,ri.unit_price_vnd
                FROM public.return_request_item ri JOIN public.order_item i ON i.order_item_id=ri.order_item_id
                WHERE ri.return_id IN (:ids) ORDER BY ri.order_item_id
                """).param("ids", ids).query((rs, row) -> new Owned<>(rs.getObject("return_id", UUID.class), item(rs))).list());
        Map<UUID, List<Evidence>> evidence = grouped(jdbc.sql("""
                SELECT return_id,image_id FROM public.return_evidence
                WHERE return_id IN (:ids) ORDER BY uploaded_at,image_id
                """).param("ids", ids).query((rs, row) -> new Owned<>(rs.getObject("return_id", UUID.class),
                new Evidence(rs.getObject("image_id", UUID.class)))).list());
        Map<UUID, List<History>> history = grouped(jdbc.sql("""
                SELECT return_id,to_status,reason,changed_at FROM public.return_status_history
                WHERE return_id IN (:ids) ORDER BY history_id
                """).param("ids", ids).query((rs, row) -> new Owned<>(rs.getObject("return_id", UUID.class),
                new History(Status.valueOf(rs.getString("to_status")), rs.getString("reason"),
                        rs.getObject("changed_at", OffsetDateTime.class)))).list());
        return headers.stream().map(r -> new ReturnResponse(r.id(), r.orderId(), r.orderNumber(), r.status(), r.reason(),
                r.decisionReason(), r.amount(), r.createdAt(), items.getOrDefault(r.id(), List.of()),
                evidence.getOrDefault(r.id(), List.of()), history.getOrDefault(r.id(), List.of()))).toList();
    }

    private static <T> Map<UUID, List<T>> grouped(List<Owned<T>> rows) {
        Map<UUID, List<T>> result = new HashMap<>();
        for (var row : rows) {
            result.computeIfAbsent(row.returnId(), ignored -> new ArrayList<>()).add(row.value());
        }
        return result;
    }

    private static Item item(ResultSet rs) throws SQLException {
        return new Item(rs.getObject("order_item_id", UUID.class), rs.getString("product_name_snapshot"),
                rs.getString("sku_snapshot"), rs.getString("color_snapshot"), rs.getString("size_snapshot"),
                rs.getInt("quantity"), rs.getLong("unit_price_vnd"));
    }

    private static Header header(ResultSet rs, int row) throws SQLException {
        return new Header(rs.getObject("return_id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getString("order_number"), Status.valueOf(rs.getString("return_status")), rs.getString("reason"),
                rs.getString("decision_reason"), rs.getLong("amount_vnd"), rs.getObject("created_at", OffsetDateTime.class));
    }

    public record OwnedOrder(String status, OffsetDateTime completedAt, UUID paymentEventId) { }

    public record ExistingRequest(UUID id, String fingerprint) { }

    private record Header(UUID id, UUID orderId, String orderNumber, Status status, String reason, String decisionReason,
                          long amount, OffsetDateTime createdAt) { }

    private record Owned<T>(UUID returnId, T value) { }
}
