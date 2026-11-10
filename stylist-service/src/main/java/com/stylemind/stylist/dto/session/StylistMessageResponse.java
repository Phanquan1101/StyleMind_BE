package com.stylemind.stylist.dto.session;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record StylistMessageResponse(
        @JsonProperty("message_id") UUID messageId,
        @JsonProperty("speaker_role") String speakerRole,
        String content,
        @JsonProperty("sent_at") Instant sentAt) {
}
