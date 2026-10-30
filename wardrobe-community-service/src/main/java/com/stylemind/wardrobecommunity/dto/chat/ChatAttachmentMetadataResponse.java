package com.stylemind.wardrobecommunity.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

/** Existing metadata only. No object key, cloud URL or unimplemented download link. */
public record ChatAttachmentMetadataResponse(
        @JsonProperty("chat_attachment_id") UUID chatAttachmentId,
        @JsonProperty("display_order") short displayOrder,
        @JsonProperty("content_type") String contentType,
        @JsonProperty("size_bytes") long sizeBytes) {
}

