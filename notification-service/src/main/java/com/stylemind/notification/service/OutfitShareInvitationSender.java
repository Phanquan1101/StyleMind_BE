package com.stylemind.notification.service;

import com.stylemind.notification.dto.OutfitShareInvitationRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.net.SocketTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

@Service
public class OutfitShareInvitationSender {

    private final JavaMailSender mailSender;
    private final String from;

    public OutfitShareInvitationSender(JavaMailSender mailSender, @Value("${stylemind.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(OutfitShareInvitationRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(request.recipientEmail());
            helper.setSubject(subject(request.sharerName()));
            helper.setText(text(request), html(request));
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            throw new MailDeliveryException(hasSocketTimeout(exception));
        }
    }

    /** Control characters (including line breaks) never reach the subject header. */
    static String subject(String sharerName) {
        String safeName = sharerName.replaceAll("\\p{Cntrl}", " ").trim();
        return safeName + " đã chia sẻ Outfit với bạn";
    }

    static String text(OutfitShareInvitationRequest request) {
        return request.sharerName() + " đã chia sẻ Outfit \"" + request.outfitName() + "\" với bạn.\n\n"
                + "Xem Outfit: " + request.outfitUrl() + "\n\n"
                + "Nếu bạn không mong đợi email này, bạn có thể bỏ qua nó.";
    }

    static String html(OutfitShareInvitationRequest request) {
        String sharer = HtmlUtils.htmlEscape(request.sharerName());
        String outfit = HtmlUtils.htmlEscape(request.outfitName());
        String url = HtmlUtils.htmlEscape(request.outfitUrl());
        return "<!doctype html><html lang=\"vi\"><body style=\"font-family:Arial,Helvetica,sans-serif;color:#222\">"
                + "<p><strong>" + sharer + "</strong> đã chia sẻ Outfit \"<strong>" + outfit + "</strong>\" với bạn.</p>"
                + "<p><a href=\"" + url + "\" style=\"display:inline-block;padding:10px 20px;background:#222;"
                + "color:#fff;text-decoration:none;border-radius:4px\">VIEW OUTFIT</a></p>"
                + "<p style=\"color:#666;font-size:12px\">Nếu bạn không mong đợi email này, bạn có thể bỏ qua nó.</p>"
                + "</body></html>";
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
