package com.stylemind.identity.client.notification;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "notification-service",
        url = "${stylemind.notification.base-url}",
        configuration = NotificationFeignConfiguration.class)
public interface NotificationFeignClient {

    String SERVICE_TOKEN_HEADER = "X-StyleMind-Service-Token";

    @PostMapping(path = "/internal/v1/email-verifications", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sendVerificationEmail(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody EmailVerificationRequest request);

    @PostMapping(path = "/internal/v1/password-resets", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> sendPasswordResetEmail(
            @RequestHeader(SERVICE_TOKEN_HEADER) String serviceToken,
            @RequestBody PasswordResetRequest request);

    record EmailVerificationRequest(String recipientEmail, String verificationCode) {
        @Override
        public String toString() {
            return "EmailVerificationRequest[recipientEmail=[REDACTED], verificationCode=[REDACTED]]";
        }
    }

    record PasswordResetRequest(String recipientEmail, String resetCode) {
        @Override
        public String toString() {
            return "PasswordResetRequest[recipientEmail=[REDACTED], resetCode=[REDACTED]]";
        }
    }
}
