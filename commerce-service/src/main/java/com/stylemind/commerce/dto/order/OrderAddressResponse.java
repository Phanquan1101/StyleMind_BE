package com.stylemind.commerce.dto.order;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record OrderAddressResponse(
        @JsonProperty("source_address_id") UUID sourceAddressId,
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("recipient_phone") String recipientPhone,
        @JsonProperty("address_line") String addressLine) {
}
