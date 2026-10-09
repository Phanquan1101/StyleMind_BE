package com.stylemind.identity.model;

public record AddressPatch(boolean labelPresent, String label, boolean recipientNamePresent, String recipientName,
        boolean recipientPhonePresent, String recipientPhone, boolean addressLinePresent, String addressLine,
        boolean wardCodePresent, String wardCode) {
}
