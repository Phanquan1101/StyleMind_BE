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

/** Persist both canonical friends atomically; database slots alone do not establish authorization. */
@Entity @Table(name = "chat_participant", schema = "public")
@Getter @Setter @NoArgsConstructor
public class ChatParticipant {
    @Id @Column(name = "chat_participant_id") private UUID chatParticipantId;
    @Column(name = "chat_session_id", nullable = false) private UUID chatSessionId;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Column(name = "participant_slot", nullable = false) private short participantSlot;
    @Column(name = "joined_at", nullable = false) private OffsetDateTime joinedAt;
}

