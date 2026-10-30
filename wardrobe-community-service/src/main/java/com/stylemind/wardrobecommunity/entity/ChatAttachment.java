package com.stylemind.wardrobecommunity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Private image metadata only; storage keys must never become public download credentials. */
@Entity @Table(name = "chat_attachment", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ChatAttachment {
    @Id @Column(name = "chat_attachment_id") private UUID chatAttachmentId;
    @Column(name = "chat_message_id", nullable = false) private UUID chatMessageId;
    @Column(name = "display_order", nullable = false) private short displayOrder;
    @Column(name = "storage_object_key", nullable = false, columnDefinition = "text") private String storageObjectKey;
    @Column(name = "content_type", nullable = false, columnDefinition = "text") private String contentType;
    @Column(name = "size_bytes", nullable = false) private long sizeBytes;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}

