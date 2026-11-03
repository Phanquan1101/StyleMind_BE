package com.stylemind.wardrobecommunity.model.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;
import java.util.List;

public record ChatImageMessageSnapshot(ChatMessageResponse message, List<ChatStoredImage> images) {
    public ChatImageMessageSnapshot { images = List.copyOf(images); }
}
