package com.stylemind.payment.entity;

import com.stylemind.payment.client.CommerceRefundSource;
import com.stylemind.payment.dto.RefundSourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refund_case", schema = "public")
public class RefundCaseEntity {

    public enum Status { AWAITING_DETAILS, PENDING, PROCESSING, REFUNDED, FAILED }

    @Id
    @Column(name = "refund_id")
    private UUID id;
    @Enumerated(EnumType.STRING)
    private RefundSourceType sourceType;
    private UUID sourceId;
    private UUID orderId;
    private UUID customerId;
    private UUID transactionId;
    private long amountVnd;
    @Enumerated(EnumType.STRING)
    private Status status;
    private byte[] destinationCiphertext;
    private UUID assignedAdminId;
    private String transferReference;
    private String proofReference;
    private String failureReason;
    private Instant refundedAt;
    private Instant updatedAt;

    protected RefundCaseEntity() { }

    public RefundCaseEntity(CommerceRefundSource source, UUID transactionId, Instant now) {
        this.id = UUID.randomUUID();
        this.sourceType = source.type();
        this.sourceId = source.id();
        this.orderId = source.orderId();
        this.customerId = source.customerId();
        this.transactionId = transactionId;
        this.amountVnd = source.amountVnd();
        this.status = Status.AWAITING_DETAILS;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public RefundSourceType getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public long getAmountVnd() { return amountVnd; }
    public Status getStatus() { return status; }
    public byte[] getDestinationCiphertext() { return destinationCiphertext; }
    public UUID getAssignedAdminId() { return assignedAdminId; }
    public String getTransferReference() { return transferReference; }
    public String getCompletionId() {
        return ("internal-refund-completion:" + id).equals(transferReference) && proofReference != null
                && proofReference.startsWith("receipt-sha256:" + id + ":") ? transferReference : null;
    }
    public String getFailureReason() { return failureReason; }
    public Instant getRefundedAt() { return refundedAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean matchesSource(CommerceRefundSource source, UUID settledTransactionId) {
        return source.type() == sourceType && source.id().equals(sourceId) && source.orderId().equals(orderId)
                && source.customerId().equals(customerId) && settledTransactionId.equals(transactionId)
                && source.amountVnd() == amountVnd;
    }

    public boolean matchesEvidence(String transfer, String proof) {
        return transfer.equals(transferReference) && proof.equals(proofReference);
    }

    public boolean matchesReceiptCompletion(String hash) {
        return matchesEvidence("internal-refund-completion:" + id, "receipt-sha256:" + id + ":" + hash);
    }

    public void completeWithReceipt(String hash, Instant now) {
        // V3 fields hold an internal completion identifier and case-bound receipt digest, never a bank reference.
        complete("internal-refund-completion:" + id, "receipt-sha256:" + id + ":" + hash, now);
    }

    public void saveDestination(byte[] ciphertext, Instant now) {
        this.destinationCiphertext = ciphertext;
        this.status = Status.PENDING;
        this.updatedAt = now;
    }

    public void start(UUID admin, Instant now) {
        this.assignedAdminId = admin;
        this.status = Status.PROCESSING;
        this.failureReason = null;
        this.updatedAt = now;
    }

    public void complete(String transfer, String proof, Instant now) {
        this.status = Status.REFUNDED;
        this.transferReference = transfer;
        this.proofReference = proof;
        this.refundedAt = now;
        this.updatedAt = now;
    }

    public void fail(String reason, Instant now) {
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
    }
}
