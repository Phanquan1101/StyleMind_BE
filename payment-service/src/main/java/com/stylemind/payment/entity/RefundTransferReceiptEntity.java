package com.stylemind.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_transfer_receipt", schema = "public")
public class RefundTransferReceiptEntity {
    @Id @Column(name = "refund_id") private UUID refundId;
    private String contentType;
    private byte[] content;
    private String contentSha256;
    private UUID uploadedByUserId;
    private Instant uploadedAt;

    protected RefundTransferReceiptEntity() { }

    public RefundTransferReceiptEntity(UUID refundId, String contentType, byte[] content, String hash, UUID admin, Instant now) {
        this.refundId = refundId; this.contentType = contentType; this.content = content.clone();
        this.contentSha256 = hash; this.uploadedByUserId = admin; this.uploadedAt = now;
    }
    public String getContentType() { return contentType; }
    public byte[] getContent() { return content.clone(); }
    public String getContentSha256() { return contentSha256; }
}
