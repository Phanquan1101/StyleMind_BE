package com.stylemind.payment.mapper;

import com.stylemind.payment.dto.RefundResponse;
import com.stylemind.payment.entity.RefundCaseEntity;

public final class RefundMapper {
    private RefundMapper() { }

    public static RefundResponse response(RefundCaseEntity refund, boolean hasReceipt) {
        return new RefundResponse(refund.getId(), refund.getSourceType(), refund.getSourceId(), refund.getOrderId(),
                refund.getAmountVnd(), refund.getStatus().name(),
                refund.getCompletionId() == null ? refund.getTransferReference() : null, refund.getCompletionId(),
                refund.getFailureReason(), refund.getRefundedAt(), refund.getUpdatedAt(), hasReceipt);
    }
}
