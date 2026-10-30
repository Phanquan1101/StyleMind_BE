package com.stylemind.wardrobecommunity.model.chat;

import java.util.UUID;

public record ChatTextCommand(UUID clientMessageId, String content) {
    @Override public String toString() {
        return "ChatTextCommand[clientMessageId=" + clientMessageId + ", content=[REDACTED]]";
    }
}

