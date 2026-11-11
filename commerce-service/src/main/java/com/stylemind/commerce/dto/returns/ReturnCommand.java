package com.stylemind.commerce.dto.returns;

import com.stylemind.commerce.dto.returns.ReturnResponse.Status;
import java.util.Map;
import java.util.UUID;

public record ReturnCommand(UUID requestKey, String reason, Map<UUID, Integer> quantities) {

    public record Decision(Action action, String reason) { }

    public record Image(String contentType, byte[] bytes) { }

    public enum Action {
        REQUEST_IMAGES(Status.REQUESTED, Status.MORE_IMAGES_REQUESTED, true),
        APPROVE_PICKUP(Status.REQUESTED, Status.PICKUP_APPROVED, false),
        REJECT(Status.REQUESTED, Status.REJECTED, true),
        PASS_INSPECTION(Status.PICKUP_APPROVED, Status.PENDING_REFUND, false),
        FAIL_INSPECTION(Status.PICKUP_APPROVED, Status.REJECTED, true);

        private final Status from;
        private final Status to;
        private final boolean requiresReason;

        Action(Status from, Status to, boolean requiresReason) {
            this.from = from;
            this.to = to;
            this.requiresReason = requiresReason;
        }

        public Status from() { return from; }
        public Status to() { return to; }
        public boolean requiresReason() { return requiresReason; }
    }
}
