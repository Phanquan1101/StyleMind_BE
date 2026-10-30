package com.stylemind.wardrobecommunity.model.chat;

import com.stylemind.wardrobecommunity.dto.chat.ChatSessionResponse;

/** Application result: the HTTP layer uses created to choose 201 or 200, returning only session. */
public record ChatSessionOpenResult(ChatSessionResponse session, boolean created) {
}

