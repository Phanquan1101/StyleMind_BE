package com.stylemind.notification;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stylemind.notification.dto.EmailVerificationRequest;
import com.stylemind.notification.service.MailDeliveryException;
import com.stylemind.notification.service.VerificationEmailSender;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class VerificationEmailSenderTests {

    @Test
    void smtpTimeoutIsClassifiedAsUnknownWithoutLoggingSensitivePayload() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("SMTP timeout", new SocketTimeoutException("timeout")))
                .when(mailSender).send(message);
        VerificationEmailSender sender = new VerificationEmailSender(mailSender, "no-reply@stylemind.local");

        assertThatThrownBy(() -> sender.send(new EmailVerificationRequest("test@example.test", "012345")))
                .isInstanceOfSatisfying(MailDeliveryException.class, failure ->
                        org.assertj.core.api.Assertions.assertThat(failure.unknown()).isTrue());
    }
}
