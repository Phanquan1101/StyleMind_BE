package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChatMessageResponse(
        @JsonProperty("chat_message_id") UUID chatMessageId,
        @JsonProperty("chat_session_id") UUID chatSessionId,
        @JsonProperty("sender_account_id") UUID senderAccountId,
        @JsonProperty("client_message_id") UUID clientMessageId,
        @JsonProperty("sequence_number") long sequenceNumber,
        String content,
        @JsonProperty("created_at") OffsetDateTime createdAt,
        List<ChatAttachmentMetadataResponse> attachments) {
    public ChatMessageResponse { attachments = List.copyOf(attachments); }
    @Override public String toString() {
        return "ChatMessageResponse[messageId=" + chatMessageId + ", content=[REDACTED], attachments=" + attachments.size() + "]";
    }
}

