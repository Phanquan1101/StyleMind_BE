package com.stylemind.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.stylemind.payment.config.CommerceClientProperties;
import com.stylemind.payment.repository.PaymentOutboxRepository;
import com.stylemind.payment.repository.PaymentOutboxRepository.PendingEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Service
public class PaymentEventDispatcher {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventDispatcher.class);
    private static final Set<String> OUTCOMES = Set.of("APPLIED", "DUPLICATE", "RECONCILIATION_REQUIRED");
    private final PaymentOutboxRepository repository;
    private final PaymentEventDeliveryService delivery;
    private final CommerceClientProperties properties;
    private final ObjectMapper mapper;
    private final RestClient http;
    private final boolean enabled;
    private PendingEvent cursor;

    public PaymentEventDispatcher(PaymentOutboxRepository repository, PaymentEventDeliveryService delivery,
            CommerceClientProperties properties, ObjectMapper mapper, RestClient.Builder builder,
            @Value("${stylemind.payment.events.delivery-enabled:false}") boolean enabled) {
        this.repository = repository; this.delivery = delivery; this.properties = properties;
        this.mapper = mapper; this.enabled = enabled;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2)); factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = builder.requestFactory(factory).build();
    }
    @Scheduled(fixedDelayString = "${stylemind.payment.events.poll-delay-ms:10000}")
    public void poll() { if (enabled) dispatchPending(); }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public synchronized int dispatchPending() {
        if (!StringUtils.hasText(properties.baseUrl()) || !StringUtils.hasText(properties.serviceToken())) return 0;
        var events = repository.pendingAfter(cursor, 25);
        if (events.isEmpty() && cursor != null) { cursor = null; events = repository.pendingAfter(null, 25); }
        int acknowledged = 0;
        for (var event : events) {
            cursor = event; // Move past failures too, so a poison row cannot starve the next batch.
            try {
                var payload = (ObjectNode) mapper.readTree(event.payload());
                payload.put("event_id", event.id().toString()); payload.put("event_type", "PaymentConfirmed");
                payload.put("payment_method", "SEPAY_VIETQR"); // Also supports existing six-field local outbox rows.
                var response = http.post().uri(properties.baseUrl() + "/internal/v1/payment-events")
                        .header("X-StyleMind-Service-Token", properties.serviceToken()).body(payload).retrieve().toEntity(String.class);
                var ack = response.getStatusCode().value() == 200 && response.getBody() != null
                        ? mapper.readTree(response.getBody()) : null;
                if (ack == null || !event.id().toString().equals(ack.path("event_id").asText())
                        || !OUTCOMES.contains(ack.path("outcome").asText()) || !ack.path("reason").isTextual())
                    throw new IllegalStateException("Invalid payment event acknowledgement");
                delivery.acknowledge(event, ack.path("outcome").asText(), Instant.now());
                acknowledged++;
            } catch (Exception failure) {
                // HTTP failures can contain headers/body/secrets. Log only our synthetic event identifier.
                log.warn("Payment event remains pending; eventId={}", event.id());
            }
        }
        return acknowledged;
    }
}
