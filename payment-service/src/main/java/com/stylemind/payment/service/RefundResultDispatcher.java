package com.stylemind.payment.service;

import com.stylemind.payment.client.RestCommerceRefundClient;
import com.stylemind.payment.repository.RefundResultOutboxRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

@Service
public class RefundResultDispatcher {

    private static final Logger log = LoggerFactory.getLogger(RefundResultDispatcher.class);
    private final RefundResultOutboxRepository outbox;
    private final RestCommerceRefundClient commerce;
    private final boolean enabled;

    public RefundResultDispatcher(RefundResultOutboxRepository outbox, RestCommerceRefundClient commerce,
            @Value("${stylemind.payment.events.delivery-enabled:false}") boolean enabled) {
        this.outbox = outbox;
        this.commerce = commerce;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${stylemind.payment.events.poll-delay-ms:10000}")
    public void poll() {
        if (enabled) {
            dispatchPending();
        }
    }

    public synchronized int dispatchPending() {
        if (!commerce.configured()) {
            return 0;
        }
        Instant checkedAt = Instant.now();
        int published = 0;
        for (var result : outbox.due(checkedAt, PageRequest.ofSize(25))) {
            boolean delivered;
            try {
                delivered = commerce.sendResult(result);
            } catch (RestClientException failure) {
                delivered = false;
            }
            if (delivered) {
                published += outbox.markPublished(result.refundId(), Instant.now());
            } else {
                outbox.retry(result.refundId(), checkedAt, Instant.now().plusSeconds(30));
                log.warn("Refund result remains pending; refundId={}", result.refundId());
            }
        }
        return published;
    }
}
