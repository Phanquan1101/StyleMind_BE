package com.stylemind.commerce.client.payment;

import com.stylemind.commerce.errors.ApiOperationException;
import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PaymentClient {
    private final RestClient client;
    private final String baseUrl;
    private final String token;

    public PaymentClient(RestClient.Builder builder,
            @Value("${stylemind.payment.base-url:}") String baseUrl,
            @Value("${stylemind.payment.handoff-token:}") String token) {
        this.baseUrl = baseUrl;
        this.token = token;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(5));
        // Fixed destination, bounded timeouts, no automatic replay of uncertain POSTs.
        this.client = builder.requestFactory(factory).build();
    }

    public PaymentAttemptResponse create(OrderPaymentRequest request) {
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(token)) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_UNAVAILABLE");
        }
        try {
            var response = client.post()
                    .uri(URI.create(baseUrl + "/internal/v1/payments/order-attempts"))
                    .header("X-StyleMind-Service-Token", token)
                    .contentType(MediaType.APPLICATION_JSON).body(request)
                    .retrieve()
                    .onStatus(s -> s.value() == 409, (req, res) -> {
                        throw failure(HttpStatus.CONFLICT, "PAYMENT_ATTEMPT_CONFLICT");
                    })
                    .onStatus(s -> s.value() != 201, (req, res) -> {
                        throw failure(HttpStatus.BAD_GATEWAY, "PAYMENT_DELIVERY_UNKNOWN");
                    })
                    .body(PaymentAttemptResponse.class);
            if (response == null || response.id() == null || response.reference() == null
                    || response.status() == null || !request.orderId().equals(response.orderId())
                    || !request.paymentMethod().equals(response.method())
                    || request.acceptedTotalVnd() != response.amountVnd()) {
                throw failure(HttpStatus.BAD_GATEWAY, "PAYMENT_DELIVERY_UNKNOWN");
            }
            return response;
        } catch (ResourceAccessException exception) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_DELIVERY_UNKNOWN");
        } catch (RestClientException | IllegalArgumentException exception) {
            throw failure(HttpStatus.BAD_GATEWAY, "PAYMENT_DELIVERY_UNKNOWN");
        }
    }

    private ApiOperationException failure(HttpStatus status, String code) {
        return new ApiOperationException(status, code,
                "Payment could not be confirmed; the order remains saved. Retry using the order payment endpoint.");
    }
}
