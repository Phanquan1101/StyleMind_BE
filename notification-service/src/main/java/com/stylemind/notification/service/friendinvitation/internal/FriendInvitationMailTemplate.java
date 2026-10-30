package com.stylemind.notification.service.friendinvitation.internal;

import com.stylemind.notification.dto.FriendInvitationEmailRequest;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.springframework.web.util.HtmlUtils;

/** Pure rendering; invitation/token/expiry state remains owned by Wardrobe Community. */
public final class FriendInvitationMailTemplate {
    private FriendInvitationMailTemplate() {}

    public record Content(String text, String html) {
        @Override public String toString() { return "Content[[REDACTED]]"; }
    }

    public static Content render(FriendInvitationEmailRequest request) {
        String expiry = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(request.expiresAt().withOffsetSameInstant(ZoneOffset.UTC));
        String text = request.senderName() + " đã mời bạn kết bạn trên StyleMind.\n\n"
                + "Mở lời mời: " + request.invitationUrl() + "\n"
                + "Đăng nhập bằng đúng email nhận lời mời. Nếu chưa có tài khoản, hãy đăng ký và xác minh email trước.\n"
                + "Mở liên kết chưa tự động kết bạn; bạn cần xác nhận lời mời.\n"
                + "Lời mời hết hạn lúc " + expiry + " (UTC).\n"
                + "Nếu không mong đợi lời mời này, bạn có thể bỏ qua email.";
        String html = "<p><strong>" + HtmlUtils.htmlEscape(request.senderName())
                + "</strong> đã mời bạn kết bạn trên StyleMind.</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(request.invitationUrl()) + "\">MỞ LỜI MỜI KẾT BẠN</a></p>"
                + "<p>Đăng nhập bằng đúng email nhận lời mời. Nếu chưa có tài khoản, hãy đăng ký và xác minh email trước.</p>"
                + "<p>Mở liên kết chưa tự động kết bạn; bạn cần xác nhận lời mời.</p>"
                + "<p>Lời mời hết hạn lúc " + expiry + " (UTC).</p>"
                + "<p>Nếu không mong đợi lời mời này, bạn có thể bỏ qua email.</p>";
        return new Content(text, html);
    }
}
