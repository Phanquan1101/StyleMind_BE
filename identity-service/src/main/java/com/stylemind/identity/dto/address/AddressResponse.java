package com.stylemind.identity.dto.address;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.stylemind.identity.model.address.AddressSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"address_id", "label", "recipient_name", "recipient_phone", "address_line", "ward_code", "is_default"})
@Schema(name = "AddressResponse")
public record AddressResponse(
        @JsonProperty("address_id") UUID addressId,
        @JsonProperty("label") String label,
        @JsonProperty("recipient_name") String recipientName,
        @JsonProperty("recipient_phone") String recipientPhone,
        @JsonProperty("address_line") String addressLine,
        @JsonProperty("ward_code") String wardCode,
        @JsonProperty("is_default") boolean isDefault) {

    public static AddressResponse from(AddressSnapshot value) {
        return new AddressResponse(value.addressId(), value.label(), value.recipientName(), value.recipientPhone(),
                value.addressLine(), value.wardCode(), value.isDefault());
    }
}
