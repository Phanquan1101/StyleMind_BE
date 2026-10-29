package com.stylemind.payment.client;

import com.stylemind.payment.config.CommerceClientProperties;
import com.stylemind.payment.errors.PaymentApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class RestCommerceOrderClient implements CommerceOrderClient {

    private static final String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    private final CommerceClientProperties properties;
    private final RestClient restClient;

    public RestCommerceOrderClient(CommerceClientProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public CommerceOrderPaymentSnapshot getPaymentSnapshot(UUID orderId) {
        if (!StringUtils.hasText(properties.baseUrl())) {
            throw new PaymentApiException(HttpStatus.SERVICE_UNAVAILABLE, "COMMERCE_CONTRACT_UNAVAILABLE",
                    "Commerce order payment snapshot endpoint is not configured");
        }
        try {
            RestClient.RequestHeadersSpec<?> request = restClient.get()
                    .uri(properties.baseUrl() + "/internal/v1/orders/{orderId}/payment-snapshot", orderId);
            if (StringUtils.hasText(properties.serviceToken())) {
                request = request.header(SERVICE_TOKEN_HEADER, properties.serviceToken());
            }
            CommerceOrderPaymentSnapshot snapshot = request.retrieve()
                    .onStatus(status -> status.value() == 404, (req, res) -> {
                        throw new PaymentApiException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND",
                                "Order was not found");
                    })
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(), (req, res) -> {
                        throw new PaymentApiException(HttpStatus.BAD_GATEWAY, "COMMERCE_ORDER_LOOKUP_FAILED",
                                "Commerce order payment snapshot lookup failed");
                    })
                    .body(CommerceOrderPaymentSnapshot.class);
            if (snapshot == null || snapshot.customerId() == null
                    || snapshot.paymentMethod() == null || snapshot.acceptedTotalVnd() <= 0) {
                throw new PaymentApiException(HttpStatus.BAD_GATEWAY, "COMMERCE_ORDER_SNAPSHOT_INVALID",
                        "Commerce order payment snapshot is invalid");
            }
            return snapshot;
        } catch (PaymentApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new PaymentApiException(HttpStatus.BAD_GATEWAY, "COMMERCE_ORDER_LOOKUP_FAILED",
                    "Commerce order payment snapshot lookup failed");
        }
    }
}
