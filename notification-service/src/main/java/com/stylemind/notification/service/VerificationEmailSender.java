package com.stylemind.notification.service;

import com.stylemind.notification.dto.EmailVerificationRequest;
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
public class VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final String from;

    public VerificationEmailSender(JavaMailSender mailSender, @Value("${stylemind.email.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void send(EmailVerificationRequest request) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(request.recipientEmail());
            helper.setSubject("Xác minh địa chỉ email cho tài khoản StyleMind");

            String code = request.verificationCode();
            String plainText = """
                    Xin chào bạn,

                    Cảm ơn bạn đã đăng ký tài khoản StyleMind. Để hoàn tất đăng ký, vui lòng nhập mã xác minh dưới đây trên màn hình xác minh email:

                    %s

                    Mã xác minh có hiệu lực trong 5 phút.

                    Nếu bạn không thực hiện đăng ký StyleMind, bạn có thể bỏ qua email này.

                    Trân trọng,
                    Đội ngũ StyleMind
                    """.formatted(code);
            String htmlBody = """
                    <!doctype html>
                    <html lang="vi">
                      <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <title>Xác minh địa chỉ email</title>
                      </head>
                      <body style="margin:0;padding:0;background-color:#f4f1ee;font-family:Arial,Helvetica,sans-serif;color:#292725;">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background-color:#f4f1ee;padding:32px 12px;">
                          <tr>
                            <td align="center">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="max-width:560px;background-color:#ffffff;border:1px solid #e8e3df;border-radius:12px;">
                                <tr>
                                  <td style="padding:32px 28px;">
                                    <p style="margin:0 0 24px;font-family:Georgia,'Times New Roman',serif;font-size:28px;letter-spacing:-1px;">StyleMind</p>
                                    <h1 style="margin:0 0 20px;font-size:22px;line-height:1.35;">Xác minh địa chỉ email</h1>
                                    <p style="margin:0 0 14px;font-size:15px;line-height:1.7;">Xin chào bạn,</p>
                                    <p style="margin:0 0 20px;font-size:15px;line-height:1.7;">Cảm ơn bạn đã đăng ký tài khoản StyleMind. Để hoàn tất đăng ký, vui lòng nhập mã xác minh dưới đây trên màn hình xác minh email:</p>
                                    <div style="margin:0 0 20px;padding:18px 12px;border-radius:8px;background-color:#f7f4f1;text-align:center;">
                                      <span style="font-size:30px;font-weight:700;letter-spacing:8px;color:#1c1b1b;">%s</span>
                                    </div>
                                    <p style="margin:0 0 14px;font-size:14px;line-height:1.7;color:#5e5a56;">Mã xác minh có hiệu lực trong <strong>5 phút</strong>.</p>
                                    <p style="margin:0 0 24px;font-size:14px;line-height:1.7;color:#5e5a56;">Nếu bạn không thực hiện đăng ký StyleMind, bạn có thể bỏ qua email này.</p>
                                    <p style="margin:0;font-size:14px;line-height:1.7;">Trân trọng,<br><strong>Đội ngũ StyleMind</strong></p>
                                  </td>
                                </tr>
                              </table>
                              <p style="margin:16px 0 0;font-size:12px;line-height:1.6;color:#77716c;">StyleMind · Email xác minh đăng ký</p>
                            </td>
                          </tr>
                        </table>
                      </body>
                    </html>
                    """.formatted(HtmlUtils.htmlEscape(code));
            helper.setText(plainText, htmlBody);
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
