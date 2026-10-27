package com.stylemind.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stylemind.notification.dto.OutfitShareInvitationRequest;
import com.stylemind.notification.service.MailDeliveryException;
import com.stylemind.notification.service.OutfitShareInvitationSender;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

class OutfitShareInvitationSenderTests {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
    private final OutfitShareInvitationSender sender = new OutfitShareInvitationSender(mailSender,
            "no-reply@stylemind.local");

    @Test
    void sendsVietnameseHtmlAndTextWithAViewOutfitLink() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        sender.send(new OutfitShareInvitationRequest("friend@example.test", "Linh Trần", "Weekend look",
                "https://app.example.test/shared/outfits/abc"));

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Linh Trần đã chia sẻ Outfit với bạn");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("friend@example.test");
        String html = part(message, "text/html");
        assertThat(html).contains("<strong>Linh Trần</strong> đã chia sẻ Outfit \"<strong>Weekend look</strong>\" với bạn")
                .contains("href=\"https://app.example.test/shared/outfits/abc\"").contains("VIEW OUTFIT");
        String text = part(message, "text/plain");
        assertThat(text).contains("Linh Trần đã chia sẻ Outfit \"Weekend look\" với bạn")
                .contains("https://app.example.test/shared/outfits/abc");
    }

    @Test
    void escapesHtmlInEveryValueAndKeepsLineBreaksOutOfTheSubject() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);

        sender.send(new OutfitShareInvitationRequest("friend@example.test", "Eve\r\nBcc: spy@example.test",
                "<script>alert(1)</script> & \"look\"", "https://app.example.test/x?a=1&b=\"2\""));

        message.saveChanges();
        assertThat(message.getSubject()).doesNotContain("\r").doesNotContain("\n");
        String html = part(message, "text/html");
        assertThat(html).doesNotContain("<script>").contains("&lt;script&gt;alert(1)&lt;/script&gt; &amp; &quot;look&quot;")
                .contains("href=\"https://app.example.test/x?a=1&amp;b=&quot;2&quot;\"");
    }

    @Test
    void smtpFailuresAreClassifiedLikeTheOtherEmails() throws Exception {
        when(mailSender.createMimeMessage()).thenReturn(message);
        OutfitShareInvitationRequest request = new OutfitShareInvitationRequest("friend@example.test", "Linh", "Look",
                "https://app.example.test/x");

        doThrow(new MailSendException("SMTP timeout", new SocketTimeoutException("timeout")))
                .when(mailSender).send(message);
        assertThatThrownBy(() -> sender.send(request)).isInstanceOfSatisfying(MailDeliveryException.class,
                failure -> assertThat(failure.unknown()).isTrue());

        doThrow(new MailSendException("rejected")).when(mailSender).send(message);
        assertThatThrownBy(() -> sender.send(request)).isInstanceOfSatisfying(MailDeliveryException.class,
                failure -> assertThat(failure.unknown()).isFalse());
    }

    private static String part(Part part, String mimeType) throws Exception {
        if (part.isMimeType(mimeType)) {
            return (String) part.getContent();
        }
        if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();
            for (int index = 0; index < multipart.getCount(); index++) {
                String found = part(multipart.getBodyPart(index), mimeType);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
