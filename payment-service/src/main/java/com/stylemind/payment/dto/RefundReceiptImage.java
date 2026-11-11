package com.stylemind.payment.dto;

public record RefundReceiptImage(String contentType, byte[] content) {
    @Override public String toString() { return "Refund receipt [redacted]"; }
}
