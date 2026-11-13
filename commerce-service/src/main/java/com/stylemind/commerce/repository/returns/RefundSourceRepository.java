package com.stylemind.commerce.repository.returns;

import com.stylemind.commerce.dto.returns.RefundSource;
import com.stylemind.commerce.dto.returns.RefundSource.Result;
import com.stylemind.commerce.dto.returns.RefundSource.Type;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RefundSourceRepository {

    private static final String RETURN_SOURCE = """
            SELECT o.order_id,o.customer_id,e.payment_attempt_id,r.amount_vnd,r.return_status AS status
            FROM public.return_request r
            JOIN public.customer_order o ON o.order_id=r.order_id
            JOIN public.payment_event_inbox e ON e.event_id=r.payment_event_id AND e.order_id=o.order_id
            WHERE r.return_id=:id AND r.return_status IN ('PENDING_REFUND','REFUNDED')
                AND o.order_status='COMPLETED' AND r.amount_vnd<=e.amount_vnd
                AND e.payment_method IN ('COD','SEPAY_VIETQR','VNPAY')
            """;

    private static final String CANCELLATION_SOURCE = """
            SELECT o.order_id,o.customer_id,e.payment_attempt_id,c.amount_vnd,c.obligation_status AS status
            FROM public.cancellation_refund_obligation c
            JOIN public.cancellation_request q ON q.cancellation_request_id=c.cancellation_request_id AND q.order_id=c.order_id
            JOIN public.customer_order o ON o.order_id=c.order_id
            JOIN public.payment_event_inbox e ON e.event_id=c.payment_event_id AND e.order_id=o.order_id
            WHERE c.cancellation_request_id=:id AND c.obligation_status IN ('PENDING_REFUND','REFUNDED')
                AND q.request_status='APPROVED' AND o.order_status='CANCELLED' AND c.amount_vnd=e.amount_vnd
                AND e.payment_method IN ('SEPAY_VIETQR','VNPAY')
            """;

    private static final String VERIFIED_PAYMENT = """
                AND e.outcome='APPLIED' AND e.customer_id=o.customer_id AND e.payment_method=o.payment_method
                AND e.amount_vnd=o.accepted_total_vnd
            """;

    private final JdbcClient jdbc;

    public Optional<StoredSource> find(Type type, UUID id, boolean lock) {
        String source = type == Type.RETURN ? RETURN_SOURCE : CANCELLATION_SOURCE;
        String lockRow = type == Type.RETURN ? " FOR UPDATE OF r" : " FOR UPDATE OF c";
        return jdbc.sql(source + VERIFIED_PAYMENT + (lock ? lockRow : "")).param("id", id)
                .query((rs, row) -> new StoredSource(new RefundSource(type, id, rs.getObject("order_id", UUID.class),
                        rs.getObject("customer_id", UUID.class), rs.getObject("payment_attempt_id", UUID.class),
                        rs.getLong("amount_vnd")), rs.getString("status").equals("REFUNDED"))).optional();
    }

    public boolean recordResult(Result result) {
        return jdbc.sql("""
                INSERT INTO public.refund_result_inbox(refund_id,source_type,source_id,order_id,amount_vnd,refunded_at)
                VALUES (:refund,:type,:source,:order,:amount,:at)
                ON CONFLICT DO NOTHING
                """).param("refund", result.refundId()).param("type", result.sourceType().name())
                .param("source", result.sourceId()).param("order", result.orderId())
                .param("amount", result.amountVnd()).param("at", result.refundedAt()).update() == 1;
    }

    public Optional<Result> findResult(UUID refundId) {
        return jdbc.sql("""
                SELECT source_type,source_id,order_id,amount_vnd,refunded_at
                FROM public.refund_result_inbox WHERE refund_id=:id
                """).param("id", refundId).query((rs, row) -> new Result(refundId, Type.valueOf(rs.getString("source_type")),
                rs.getObject("source_id", UUID.class), rs.getObject("order_id", UUID.class),
                rs.getLong("amount_vnd"), rs.getObject("refunded_at", OffsetDateTime.class))).optional();
    }

    public void markComplete(Type type, UUID id) {
        String update = type == Type.RETURN
                ? "UPDATE public.return_request SET return_status='REFUNDED' WHERE return_id=:id AND return_status='PENDING_REFUND'"
                : """
                  UPDATE public.cancellation_refund_obligation SET obligation_status='REFUNDED'
                  WHERE cancellation_request_id=:id AND obligation_status='PENDING_REFUND'
                  """;
        if (jdbc.sql(update).param("id", id).update() != 1) {
            throw new IllegalStateException("Refund source update was not applied");
        }
    }

    public record StoredSource(RefundSource source, boolean refunded) { }
}
