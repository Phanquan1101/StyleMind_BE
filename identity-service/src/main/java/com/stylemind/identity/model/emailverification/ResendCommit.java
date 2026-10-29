package com.stylemind.identity.model.emailverification;

import com.stylemind.identity.client.notification.payload.EmailDispatch;

public record ResendCommit(VerificationOutcome outcome, EmailDispatch dispatch) {
}
