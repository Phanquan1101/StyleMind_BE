package com.stylemind.payment.service.impl;

import com.stylemind.payment.client.CommerceRefundSource;
import com.stylemind.payment.client.RestCommerceRefundClient;
import com.stylemind.payment.dto.RefundRequests.Completion;
import com.stylemind.payment.dto.RefundRequests.Destination;
import com.stylemind.payment.dto.RefundRequests.Failure;
import com.stylemind.payment.dto.RefundRequests.ReceiptCompletion;
import com.stylemind.payment.dto.RefundReceiptImage;
import com.stylemind.payment.entity.RefundTransferReceiptEntity;
import com.stylemind.payment.dto.RefundResponse;
import com.stylemind.payment.dto.RefundSourceType;
import com.stylemind.payment.entity.RefundAuditEntity;
import com.stylemind.payment.entity.RefundAuditEntity.Action;
import com.stylemind.payment.entity.RefundCaseEntity;
import com.stylemind.payment.entity.RefundCaseEntity.Status;
import com.stylemind.payment.entity.RefundResultOutboxEntity;
import com.stylemind.payment.errors.RefundErrorCode;
import com.stylemind.payment.mapper.RefundMapper;
import com.stylemind.payment.repository.RefundAuditRepository;
import com.stylemind.payment.repository.RefundCaseRepository;
import com.stylemind.payment.repository.RefundLedgerRepository;
import com.stylemind.payment.repository.RefundResultOutboxRepository;
import com.stylemind.payment.security.RefundDestinationCipher;
import com.stylemind.payment.service.PaymentAuthorizationService.RefundActor;
import com.stylemind.payment.service.RefundService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefundServiceImpl implements RefundService {

    private final RestCommerceRefundClient commerce;
    private final RefundCaseRepository refunds;
    private final RefundLedgerRepository ledger;
    private final RefundAuditRepository audit;
    private final RefundResultOutboxRepository outbox;
    private final RefundDestinationCipher cipher;
    private final com.stylemind.payment.repository.RefundTransferReceiptRepository receipts;
    private final com.stylemind.payment.validation.RefundReceiptValidator receiptValidator;
    private final TransactionTemplate allocation;

    public RefundServiceImpl(RestCommerceRefundClient commerce, RefundCaseRepository refunds,
            RefundLedgerRepository ledger, RefundAuditRepository audit, RefundResultOutboxRepository outbox,
            RefundDestinationCipher cipher, PlatformTransactionManager transactionManager,
            com.stylemind.payment.repository.RefundTransferReceiptRepository receipts,
            com.stylemind.payment.validation.RefundReceiptValidator receiptValidator) {
        this.commerce = commerce;
        this.refunds = refunds;
        this.ledger = ledger;
        this.audit = audit;
        this.outbox = outbox;
        this.cipher = cipher;
        this.receipts = receipts;
        this.receiptValidator = receiptValidator;
        this.allocation = new TransactionTemplate(transactionManager);
        // The sum must see any allocation that committed while this request was waiting for the ledger lock.
        this.allocation.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Override
    public RefundResponse prepare(RefundSourceType type, UUID sourceId, RefundActor actor) {
        var existing = refunds.findBySourceTypeAndSourceId(type, sourceId);
        if (existing.isPresent()) {
            requireReadAccess(existing.get().getCustomerId(), actor);
            return response(existing.get(), actor.admin());
        }
        CommerceRefundSource source = commerce.getSource(type, sourceId);
        requireReadAccess(source.customerId(), actor);
        return allocation.execute(transaction -> allocate(source, actor.id()));
    }

    private RefundResponse allocate(CommerceRefundSource source, UUID actor) {
        refunds.lockSource(source.type().name(), source.id());
        var settled = ledger.lockSettledTransaction(source.paymentAttemptId(), source.orderId(), source.customerId())
                .orElseThrow(RefundErrorCode.REFUND_LEDGER_MISMATCH::exception);

        var existing = refunds.findBySourceTypeAndSourceId(source.type(), source.id());
        if (existing.isPresent()) {
            if (!existing.get().matchesSource(source, settled.getTransactionId())) {
                throw RefundErrorCode.REFUND_SOURCE_CHANGED.exception();
            }
            return response(existing.get(), false);
        }
        // Failed cases can be retried, so they continue to reserve the original amount.
        long allocated = refunds.allocatedAmount(settled.getTransactionId());
        if (source.amountVnd() > settled.getActualAmountVnd() - allocated) {
            throw RefundErrorCode.REFUND_AMOUNT_EXCEEDED.exception();
        }
        Instant now = now();
        var refund = refunds.save(new RefundCaseEntity(source, settled.getTransactionId(), now));
        record(refund, actor, Action.PREPARED, null, now);
        return response(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public RefundResponse get(RefundSourceType type, UUID sourceId, RefundActor actor) {
        var refund = refunds.findBySourceTypeAndSourceId(type, sourceId)
                .orElseThrow(RefundErrorCode.REFUND_NOT_FOUND::exception);
        requireReadAccess(refund.getCustomerId(), actor);
        return response(refund, actor.admin());
    }

    @Override
    @Transactional
    public RefundResponse saveDestination(RefundSourceType type, UUID sourceId, RefundActor actor, Destination destination) {
        var refund = lock(type, sourceId);
        if (!actor.customer() || !refund.getCustomerId().equals(actor.id())) {
            throw RefundErrorCode.REFUND_NOT_FOUND.exception();
        }
        Status status = refund.getStatus();
        if (status != Status.AWAITING_DETAILS && status != Status.PENDING) {
            throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
        }
        Instant now = now();
        refund.saveDestination(cipher.encrypt(refund.getId(), destination), now);
        record(refund, actor.id(), Action.DESTINATION_SAVED, null, now);
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse start(RefundSourceType type, UUID sourceId, UUID admin) {
        var refund = lock(type, sourceId);
        Status status = refund.getStatus();
        if (status == Status.PROCESSING) {
            requireAssigned(refund, admin);
            return response(refund);
        }
        if (status != Status.PENDING && status != Status.FAILED) {
            throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
        }
        cipher.decrypt(refund.getId(), refund.getDestinationCiphertext());
        Instant now = now();
        refund.start(admin, now);
        record(refund, admin, status == Status.FAILED ? Action.RETRIED : Action.STARTED, null, now);
        return response(refund);
    }

    @Override
    @Transactional
    public Destination getDestination(RefundSourceType type, UUID sourceId, UUID admin) {
        var refund = lock(type, sourceId);
        requireProcessing(refund);
        requireAssigned(refund, admin);
        var destination = cipher.decrypt(refund.getId(), refund.getDestinationCiphertext());
        record(refund, admin, Action.DESTINATION_VIEWED, null, now());
        return destination;
    }

    @Override
    @Transactional
    public RefundResponse complete(RefundSourceType type, UUID sourceId, UUID admin, Completion evidence) {
        var refund = lock(type, sourceId);
        if (refund.getStatus() == Status.REFUNDED) {
            requireAssigned(refund, admin);
            if (!refund.matchesEvidence(evidence.transferReference(), evidence.proofReference())) {
                throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
            }
            return response(refund);
        }

        requireProcessing(refund);
        requireAssigned(refund, admin);
        refunds.lockTransferReference(evidence.transferReference());
        if (refunds.existsByTransferReference(evidence.transferReference())) {
            throw RefundErrorCode.REFUND_TRANSFER_REFERENCE_IN_USE.exception();
        }
        Instant now = now();
        refund.complete(evidence.transferReference(), evidence.proofReference(), now);
        record(refund, admin, Action.REFUNDED, null, now);
        outbox.save(new RefundResultOutboxEntity(refund, now));
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse completeWithReceipt(RefundSourceType type, UUID sourceId, UUID admin,
            ReceiptCompletion completion, org.springframework.web.multipart.MultipartFile image) {
        var refund = lock(type, sourceId);
        if (refund.getStatus() != Status.REFUNDED) {
            requireProcessing(refund);
        }
        requireAssigned(refund, admin);
        if (!refund.getId().equals(completion.refundId())) {
            throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
        }
        var validated = receiptValidator.validate(image);
        String hash = receiptHash(validated);
        if (refund.getStatus() == Status.REFUNDED) {
            var existing = receipts.findById(refund.getId())
                    .orElseThrow(RefundErrorCode.REFUND_STATE_CONFLICT::exception);
            if (!hash.equals(existing.getContentSha256())) {
                throw RefundErrorCode.REFUND_RECEIPT_CONFLICT.exception();
            }
            if (!refund.matchesReceiptCompletion(hash)) {
                throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
            }
            return response(refund);
        }
        Instant now = now();
        storeReceipt(refund, validated, hash, admin, now);
        String completionId = "internal-refund-completion:" + refund.getId();
        refunds.lockTransferReference(completionId);
        if (refunds.existsByTransferReference(completionId)) {
            throw RefundErrorCode.REFUND_TRANSFER_REFERENCE_IN_USE.exception();
        }
        refund.completeWithReceipt(hash, now);
        record(refund, admin, Action.REFUNDED, null, now);
        outbox.save(new RefundResultOutboxEntity(refund, now));
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse fail(RefundSourceType type, UUID sourceId, UUID admin, Failure failure) {
        var refund = lock(type, sourceId);
        if (refund.getStatus() == Status.FAILED) {
            requireAssigned(refund, admin);
            if (!failure.reason().equals(refund.getFailureReason())) {
                throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
            }
            return response(refund);
        }

        requireProcessing(refund);
        requireAssigned(refund, admin);
        Instant now = now();
        refund.fail(failure.reason(), now);
        record(refund, admin, Action.FAILED, failure.reason(), now);
        return response(refund);
    }

    @Override
    @Transactional
    public RefundResponse saveReceipt(RefundSourceType type, UUID sourceId, UUID admin,
            org.springframework.web.multipart.MultipartFile image) {
        var refund = lock(type, sourceId);
        if (refund.getStatus() != Status.PROCESSING && refund.getStatus() != Status.REFUNDED) {
            throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
        }
        requireAssigned(refund, admin);
        var validated = receiptValidator.validate(image);
        storeReceipt(refund, validated, receiptHash(validated), admin, now());
        return response(refund);
    }

    private String receiptHash(RefundReceiptImage validated) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256").digest(validated.content()));
        } catch (java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private void storeReceipt(RefundCaseEntity refund, RefundReceiptImage image, String hash, UUID admin, Instant now) {
        var existing = receipts.findById(refund.getId());
        if (existing.isPresent()) {
            if (!hash.equals(existing.get().getContentSha256())) throw RefundErrorCode.REFUND_RECEIPT_CONFLICT.exception();
        } else {
            receipts.saveAndFlush(new RefundTransferReceiptEntity(
                    refund.getId(), image.contentType(), image.content(), hash, admin, now));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public com.stylemind.payment.dto.RefundReceiptImage receipt(RefundSourceType type, UUID sourceId, RefundActor actor) {
        var refund = refunds.findBySourceTypeAndSourceId(type, sourceId).orElseThrow(RefundErrorCode.REFUND_NOT_FOUND::exception);
        requireReadAccess(refund.getCustomerId(), actor);
        if (!actor.admin() && refund.getStatus() != Status.REFUNDED) throw RefundErrorCode.REFUND_NOT_FOUND.exception();
        var image = receipts.findById(refund.getId()).orElseThrow(RefundErrorCode.REFUND_NOT_FOUND::exception);
        return new com.stylemind.payment.dto.RefundReceiptImage(image.getContentType(), image.getContent());
    }

    private RefundResponse response(RefundCaseEntity refund) { return response(refund, true); }

    private RefundResponse response(RefundCaseEntity refund, boolean admin) {
        return RefundMapper.response(refund, (admin || refund.getStatus() == Status.REFUNDED) && receipts.existsById(refund.getId()));
    }

    private RefundCaseEntity lock(RefundSourceType type, UUID id) {
        return refunds.lockCase(type, id).orElseThrow(RefundErrorCode.REFUND_NOT_FOUND::exception);
    }

    private void record(RefundCaseEntity refund, UUID actor, Action action, String reason, Instant now) {
        audit.save(new RefundAuditEntity(refund, actor, action, reason, now));
    }

    private void requireReadAccess(UUID customerId, RefundActor actor) {
        if (!actor.admin() && !customerId.equals(actor.id())) {
            throw RefundErrorCode.REFUND_NOT_FOUND.exception();
        }
    }

    private void requireProcessing(RefundCaseEntity refund) {
        if (refund.getStatus() != Status.PROCESSING) {
            throw RefundErrorCode.REFUND_STATE_CONFLICT.exception();
        }
    }

    private void requireAssigned(RefundCaseEntity refund, UUID admin) {
        if (!admin.equals(refund.getAssignedAdminId())) {
            throw RefundErrorCode.ACCESS_DENIED.exception();
        }
    }

    private Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }
}
