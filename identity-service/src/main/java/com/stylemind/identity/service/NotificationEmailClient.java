package com.stylemind.identity.service;

import com.stylemind.identity.errors.EmailDeliveryException;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class NotificationEmailClient {

    private final RestClient client;
    private final String serviceToken;

    public NotificationEmailClient(
            @Value("${stylemind.notification.base-url}") String baseUrl,
            @Value("${stylemind.notification.service-token:}") String serviceToken,
            @Value("${stylemind.notification.connect-timeout:PT3S}") Duration connectTimeout,
            @Value("${stylemind.notification.read-timeout:PT5S}") Duration readTimeout) {
        if (serviceToken.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("STYLEMIND_SERVICE_TOKEN must contain at least 32 bytes");
        }
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.serviceToken = serviceToken;
    }

    public void send(EmailDispatch dispatch) {
        post("/internal/v1/email-verifications",
                new NotificationEmailRequest(dispatch.recipientEmail(), dispatch.verificationCode()));
    }

    public void sendPasswordReset(PasswordResetEmailDispatch dispatch) {
        post("/internal/v1/password-resets",
                new PasswordResetNotificationRequest(dispatch.recipientEmail(), dispatch.verificationCode()));
    }

    private void post(String path, Object body) {
        try {
            client.post()
                    .uri(path)
                    .header("X-StyleMind-Service-Token", serviceToken)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == HttpStatus.ACCEPTED.value()) {
                            return null;
                        }
                        if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                            throw new EmailDeliveryException(HttpStatus.SERVICE_UNAVAILABLE,
                                    "EMAIL_DELIVERY_FAILED");
                        }
                        throw unknownDelivery();
                    });
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 503) {
                throw new EmailDeliveryException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED");
            }
            throw unknownDelivery();
        } catch (ResourceAccessException exception) {
            throw unknownDelivery();
        }
    }

    private EmailDeliveryException unknownDelivery() {
        return new EmailDeliveryException(HttpStatus.GATEWAY_TIMEOUT, "EMAIL_DELIVERY_UNKNOWN");
    }

    private record NotificationEmailRequest(String recipientEmail, String verificationCode) {
        @Override
        public String toString() {
            return "NotificationEmailRequest[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
        }
    }

    private record PasswordResetNotificationRequest(String recipientEmail, String resetCode) {
        @Override
        public String toString() {
            return "PasswordResetNotificationRequest[recipientEmail=[REDACTED], resetCode=[REDACTED]]";
        }
    }
}
