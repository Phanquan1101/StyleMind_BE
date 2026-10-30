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

/** Stable conversation for one Friendship; current friendship authorization belongs in the service. */
@Entity @Table(name = "chat_session", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ChatSession {
    @Id @Column(name = "chat_session_id") private UUID chatSessionId;
    @Column(name = "friendship_id", nullable = false) private UUID friendshipId;
    @Column(name = "last_sequence_number", nullable = false) private long lastSequenceNumber;
    @Column(name = "created_at", nullable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
}

