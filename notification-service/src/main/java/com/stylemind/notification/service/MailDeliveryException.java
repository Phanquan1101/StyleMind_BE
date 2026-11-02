package com.stylemind.notification.service;

public final class MailDeliveryException extends RuntimeException {

    private final boolean unknown;

    public MailDeliveryException(boolean unknown) {
        super(unknown ? "EMAIL_DELIVERY_UNKNOWN" : "EMAIL_DELIVERY_FAILED");
        this.unknown = unknown;
    }

    public boolean unknown() {
        return unknown;
    }
}
