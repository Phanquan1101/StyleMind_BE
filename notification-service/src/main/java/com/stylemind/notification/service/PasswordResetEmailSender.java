package com.stylemind.notification.service;

import com.stylemind.notification.dto.PasswordResetEmailRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetEmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public PasswordResetEmailSender(JavaMailSender mailSender, @Value("${stylemind.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(PasswordResetEmailRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(from);
            helper.setTo(request.recipientEmail());
            helper.setSubject("Reset your StyleMind password");
            helper.setText("Your StyleMind password reset code is " + request.resetCode()
                    + ". It expires in 5 minutes. If you did not request this, you can ignore this email.", false);
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            throw new MailDeliveryException(hasSocketTimeout(exception));
        }
    }

    private boolean hasSocketTimeout(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
        }
        return false;
    }
}
