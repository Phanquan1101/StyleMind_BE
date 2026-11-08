package com.stylemind.wardrobecommunity.model.chat;

import java.util.List;
import java.util.UUID;

public record ChatImageCommand(UUID clientMessageId, String content, List<ChatImage> images) {
    public ChatImageCommand { images = List.copyOf(images); }
    @Override public String toString() { return "ChatImageCommand[content=[REDACTED], images=" + images.size() + "]"; }
}
