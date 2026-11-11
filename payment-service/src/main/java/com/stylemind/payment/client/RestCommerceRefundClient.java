package com.stylemind.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.payment.config.CommerceClientProperties;
import com.stylemind.payment.dto.RefundResult;
import com.stylemind.payment.dto.RefundSourceType;
import com.stylemind.payment.errors.PaymentApiException;
import com.stylemind.payment.errors.RefundErrorCode;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestCommerceRefundClient {

    private static final String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    private final CommerceClientProperties properties;
    private final RestClient client;

    public RestCommerceRefundClient(CommerceClientProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.client = builder.requestFactory(factory).build();
    }

    public boolean configured() {
        return StringUtils.hasText(properties.baseUrl()) && StringUtils.hasText(properties.serviceToken());
    }

    public CommerceRefundSource getSource(RefundSourceType type, UUID sourceId) {
        if (!configured()) {
            throw RefundErrorCode.COMMERCE_REFUND_SOURCE_UNAVAILABLE.exception();
        }
        try {
            JsonNode body = client.get().uri(properties.baseUrl() + "/internal/v1/refund-sources/{type}/{id}", type, sourceId)
                    .header(SERVICE_TOKEN_HEADER, properties.serviceToken())
                    .retrieve()
                    .onStatus(status -> status.value() == 404, (request, response) -> {
                        throw RefundErrorCode.REFUND_NOT_FOUND.exception();
                    })
                    .body(JsonNode.class);
            if (body == null || !type.name().equals(body.path("source_type").textValue())
                    || !sourceId.equals(uuid(body, "source_id"))) {
                throw RefundErrorCode.COMMERCE_REFUND_SOURCE_INVALID.exception();
            }
            JsonNode amount = body.path("amount_vnd");
            if (!amount.isIntegralNumber() || !amount.canConvertToLong() || amount.longValue() <= 0) {
                throw RefundErrorCode.COMMERCE_REFUND_SOURCE_INVALID.exception();
            }
            return new CommerceRefundSource(type, sourceId, uuid(body, "order_id"),
                    uuid(body, "customer_id"), uuid(body, "payment_attempt_id"), amount.longValue());
        } catch (RestClientException exception) {
            throw RefundErrorCode.COMMERCE_REFUND_SOURCE_LOOKUP_FAILED.exception();
        }
    }

    public boolean sendResult(RefundResult result) {
        return client.post().uri(properties.baseUrl() + "/internal/v1/refund-results")
                .header(SERVICE_TOKEN_HEADER, properties.serviceToken())
                .body(result)
                .retrieve()
                .toBodilessEntity().getStatusCode() == HttpStatus.NO_CONTENT;
    }

    private UUID uuid(JsonNode body, String field) {
        String raw = body.path(field).textValue();
        if (raw == null) {
            throw RefundErrorCode.COMMERCE_REFUND_SOURCE_INVALID.exception();
        }
        try {
            return RefundSourceType.parseId(raw);
        } catch (PaymentApiException exception) {
            throw RefundErrorCode.COMMERCE_REFUND_SOURCE_INVALID.exception();
        }
    }
}
