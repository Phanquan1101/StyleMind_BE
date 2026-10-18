package com.stylemind.commerce.service.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.client.identity.IdentityFeignClient;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.order.AddressSnapshot;
import feign.FeignException;
import feign.RetryableException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class IdentityAddressClient {

    private final IdentityFeignClient identityFeignClient;

    public AddressSnapshot getAddress(UUID addressId, String authorizationHeader) {
        try {
            JsonNode body = identityFeignClient.getMyAddress(authorizationHeader, addressId);
            if (body == null) {
                throw invalidAddress();
            }
            return new AddressSnapshot(addressId, required(body, "recipient_name"),
                    required(body, "recipient_phone"), required(body, "address_line"));
        } catch (RetryableException exception) {
            throw unavailable();
        } catch (FeignException exception) {
            if (exception.status() == HttpStatus.NOT_FOUND.value()) {
                throw invalidAddress();
            }
            throw unavailable();
        }
    }

    private String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalidAddress();
        }
        return value.textValue();
    }

    private ApiOperationException invalidAddress() {
        return new ApiOperationException(HttpStatus.CONFLICT, "INVALID_SHIPPING_ADDRESS",
                "Shipping address does not exist for this customer");
    }

    private ApiOperationException unavailable() {
        return new ApiOperationException(HttpStatus.BAD_GATEWAY, "IDENTITY_UNAVAILABLE",
                "Identity address validation failed");
    }
}
