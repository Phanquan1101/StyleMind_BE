package com.stylemind.wardrobecommunity.model.chat;

import java.util.List;

public record ChatSessionPageSnapshot(List<ChatSessionSnapshot> rows, long total) {
    public ChatSessionPageSnapshot {
        rows = List.copyOf(rows);
    }
}

