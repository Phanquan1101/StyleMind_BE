package com.stylemind.notification.service.friendinvitation.impl;

import com.stylemind.notification.dto.FriendInvitationEmailRequest;
import com.stylemind.notification.service.MailDeliveryException;
import com.stylemind.notification.service.friendinvitation.FriendInvitationEmailService;
import com.stylemind.notification.service.friendinvitation.internal.FriendInvitationMailTemplate;
import jakarta.mail.MessagingException;
import java.net.SocketTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class FriendInvitationEmailServiceImpl implements FriendInvitationEmailService {
    private final JavaMailSender mailSender;
    private final String from;

    public FriendInvitationEmailServiceImpl(JavaMailSender mailSender, @Value("${stylemind.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override public void send(FriendInvitationEmailRequest request) {
        try {
            var content = FriendInvitationMailTemplate.render(request);
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(request.recipientEmail());
            helper.setSubject("Lời mời kết bạn trên StyleMind");
            helper.setText(content.text(), content.html());
            mailSender.send(message);
        } catch (MailException | MessagingException failure) {
            // Never attach the provider exception: it can contain recipient/link or credentials.
            throw new MailDeliveryException(unknown(failure));
        }
    }

    private boolean unknown(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof SocketTimeoutException || cause instanceof java.net.http.HttpTimeoutException) return true;
        }
        // JavaMail may wrap per-message transport exceptions outside the ordinary cause chain.
        if (failure instanceof org.springframework.mail.MailSendException sent) {
            for (Exception nested : sent.getMessageExceptions()) {
                if (nested != failure && unknown(nested)) return true;
            }
        }
        return false;
    }
}
