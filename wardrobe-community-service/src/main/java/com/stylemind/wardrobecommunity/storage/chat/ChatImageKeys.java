package com.stylemind.wardrobecommunity.storage.chat;

import com.stylemind.wardrobecommunity.model.chat.ChatImage;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ChatImageKeys {
    private static final Pattern KEY = Pattern.compile(
            "^chat/v1/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[1-5]-([0-9a-f]{64})\\.(jpg|png|webp)$");
    private ChatImageKeys() { }
    public static String create(UUID attempt, int position, ChatImage image) {
        String extension = switch (image.contentType()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unsupported validated image type");
        };
        return "chat/v1/" + attempt + "/" + position + "-" + image.sha256() + "." + extension;
    }
    public static String sha256(String key) {
        var match = KEY.matcher(key);
        return match.matches() ? match.group(1) : null;
    }
}
