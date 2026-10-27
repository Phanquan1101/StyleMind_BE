package com.stylemind.commerce.dto.order;

import java.util.UUID;

public record PaymentEventReceipt(UUID event_id, String outcome, String reason) { }
