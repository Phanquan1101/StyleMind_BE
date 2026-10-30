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

/** Persisted message only. A null text requires images, checked by the future transactional writer. */
@Entity @Table(name = "chat_message", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ChatMessage {
    @Id @Column(name = "chat_message_id") private UUID chatMessageId;
    @Column(name = "chat_session_id", nullable = false) private UUID chatSessionId;
    @Column(name = "sender_account_id", nullable = false) private UUID senderAccountId;
    @Column(name = "client_message_id", nullable = false) private UUID clientMessageId;
    @Column(name = "sequence_number", nullable = false) private long sequenceNumber;
    @Column(name = "content", columnDefinition = "text") private String content;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
}

