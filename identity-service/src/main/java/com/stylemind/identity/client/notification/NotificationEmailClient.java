package com.stylemind.identity.client.notification;

import com.stylemind.identity.client.notification.payload.EmailDispatch;
import com.stylemind.identity.client.notification.payload.PasswordResetEmailDispatch;
import com.stylemind.identity.errors.EmailDeliveryException;
import feign.FeignException;
import feign.RetryableException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class NotificationEmailClient {

    private final NotificationFeignClient notificationClient;
    private final String serviceToken;

    public NotificationEmailClient(
            NotificationFeignClient notificationClient,
            @Value("${stylemind.notification.service-token:}") String serviceToken) {
        if (serviceToken.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("STYLEMIND_SERVICE_TOKEN must contain at least 32 bytes");
        }
        this.notificationClient = notificationClient;
        this.serviceToken = serviceToken;
    }

    public void send(EmailDispatch dispatch) {
        deliver(() -> notificationClient.sendVerificationEmail(serviceToken,
                new NotificationFeignClient.EmailVerificationRequest(
                        dispatch.recipientEmail(), dispatch.verificationCode())));
    }

    public void sendPasswordReset(PasswordResetEmailDispatch dispatch) {
        deliver(() -> notificationClient.sendPasswordResetEmail(serviceToken,
                new NotificationFeignClient.PasswordResetRequest(
                        dispatch.recipientEmail(), dispatch.verificationCode())));
    }

    private void deliver(DeliveryCall call) {
        try {
            ResponseEntity<Void> response = call.execute();
            int status = response.getStatusCode().value();
            if (status == HttpStatus.ACCEPTED.value()) {
                return;
            }
            if (status == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw failedDelivery();
            }
            throw unknownDelivery();
        } catch (RetryableException exception) {
            if (exception.status() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw failedDelivery();
            }
            throw unknownDelivery();
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                throw failedDelivery();
            }
            throw unknownDelivery();
        }
    }

    private EmailDeliveryException failedDelivery() {
        return new EmailDeliveryException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED");
    }

    private EmailDeliveryException unknownDelivery() {
        return new EmailDeliveryException(HttpStatus.GATEWAY_TIMEOUT, "EMAIL_DELIVERY_UNKNOWN");
    }

    @FunctionalInterface
    private interface DeliveryCall {
        ResponseEntity<Void> execute();
    }
}
