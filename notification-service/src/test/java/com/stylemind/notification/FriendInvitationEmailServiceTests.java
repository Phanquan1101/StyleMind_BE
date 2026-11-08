package com.stylemind.notification;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.stylemind.notification.dto.FriendInvitationEmailRequest;
import com.stylemind.notification.service.MailDeliveryException;
import com.stylemind.notification.service.friendinvitation.impl.FriendInvitationEmailServiceImpl;
import com.stylemind.notification.service.friendinvitation.internal.FriendInvitationMailTemplate;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import java.time.OffsetDateTime;
import java.util.Properties;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class FriendInvitationEmailServiceTests {
    final JavaMailSender mail = mock(JavaMailSender.class);
    final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    final FriendInvitationEmailServiceImpl sender = new FriendInvitationEmailServiceImpl(mail, "no-reply@stylemind.local");
    final FriendInvitationEmailRequest input = new FriendInvitationEmailRequest("friend@example.test", "Linh Trần",
            "https://app.example.test/invitations#fixture-token", OffsetDateTime.parse("2026-11-04T01:02:03Z"));

    @Test void sendsVietnameseAlternativeTextAndHtmlWithExpiryAndLoginGuidance() throws Exception {
        when(mail.createMimeMessage()).thenReturn(message);
        sender.send(input);
        verify(mail).send(message);
        message.saveChanges();
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo(input.recipientEmail());
        assertThat(message.getSubject()).isEqualTo("Lời mời kết bạn trên StyleMind");
        assertThat(part(message, "text/plain")).contains(input.invitationUrl(), "2026-11-04T01:02:03Z", "xác minh email", "chưa tự động kết bạn");
        assertThat(part(message, "text/html")).contains("MỞ LỜI MỜI KẾT BẠN", "Linh Trần");
    }

    @Test void escapesHtmlAndNeverInterpolatesSenderIntoMailHeaders() throws Exception {
        when(mail.createMimeMessage()).thenReturn(message);
        sender.send(new FriendInvitationEmailRequest(input.recipientEmail(), "<img src=x>\r\nBcc: other@example.test",
                "https://app.example.test/x?a=1&b=2", input.expiresAt()));
        message.saveChanges();
        assertThat(message.getSubject()).doesNotContain("Bcc", "\r", "\n");
        assertThat(message.getRecipients(Message.RecipientType.BCC)).isNull();
        assertThat(part(message, "text/html")).doesNotContain("<img").contains("&lt;img src=x&gt;", "a=1&amp;b=2");
    }

    @Test void smtpRejectedIsSanitizedAndHasNoProviderCause() {
        when(mail.createMimeMessage()).thenReturn(message);
        doThrow(new MailSendException("private provider body")).when(mail).send(message);
        assertThatThrownBy(() -> sender.send(input)).isInstanceOfSatisfying(MailDeliveryException.class,
                e -> assertThat(e.unknown()).isFalse()).hasNoCause().hasMessage("EMAIL_DELIVERY_FAILED");
    }

    @Test void nestedMessageTimeoutIsUnknown() {
        when(mail.createMimeMessage()).thenReturn(message);
        var timeout = new MailSendException(Map.of(message, new MessagingException("private", new SocketTimeoutException())));
        doThrow(timeout).when(mail).send(message);
        assertThatThrownBy(() -> sender.send(input)).isInstanceOfSatisfying(MailDeliveryException.class,
                e -> assertThat(e.unknown()).isTrue()).hasNoCause().hasMessage("EMAIL_DELIVERY_UNKNOWN");
    }

    @Test void requestAndRenderedContentAreRedactedInDiagnostics() {
        assertThat(input.toString()).doesNotContain("friend@example.test", "fixture-token");
        assertThat(FriendInvitationMailTemplate.render(input).toString()).isEqualTo("Content[[REDACTED]]");
    }

    static String part(Part part, String type) throws Exception {
        if (part.isMimeType(type)) return (String) part.getContent();
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int i=0; i<multipart.getCount(); i++) {
                String found=part(multipart.getBodyPart(i), type); if(found!=null)return found;
            }
        }
        return null;
    }
}
