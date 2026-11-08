package com.stylemind.wardrobecommunity.model.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatMessageResponse;

public record ChatMessageSendResult(ChatMessageResponse message, boolean created) {
}

