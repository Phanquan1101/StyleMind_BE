package com.stylemind.stylist.entity.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stylist_message", schema = "public")
public class StylistMessageEntity {

    @Id
    @Column(name = "stylist_message_id", nullable = false, updatable = false)
    private UUID stylistMessageId;

    @Column(name = "stylist_session_id", nullable = false, updatable = false)
    private UUID stylistSessionId;

    @Column(name = "speaker_role", nullable = false, updatable = false, columnDefinition = "text")
    private String speakerRole;

    @Column(name = "content", nullable = false, updatable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sent_at", nullable = false, updatable = false)
    private Instant sentAt;

    protected StylistMessageEntity() {
    }

    public StylistMessageEntity(UUID stylistMessageId, UUID stylistSessionId, String speakerRole,
                                String content, Instant sentAt) {
        this.stylistMessageId = stylistMessageId;
        this.stylistSessionId = stylistSessionId;
        this.speakerRole = speakerRole;
        this.content = content;
        this.sentAt = sentAt;
    }

    public UUID getStylistMessageId() { return stylistMessageId; }
    public UUID getStylistSessionId() { return stylistSessionId; }
    public String getSpeakerRole() { return speakerRole; }
    public String getContent() { return content; }
    public Instant getSentAt() { return sentAt; }
}
