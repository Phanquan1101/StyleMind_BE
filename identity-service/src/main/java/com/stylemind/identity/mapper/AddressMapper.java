package com.stylemind.identity.mapper;

import com.stylemind.identity.dto.address.AddressCreateRequest;
import com.stylemind.identity.dto.address.AddressPatchRequest;
import com.stylemind.identity.dto.address.AddressResponse;
import com.stylemind.identity.entity.CustomerAddress;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AddressMapper {
    public CustomerAddress create(UUID owner, AddressCreateRequest request, boolean defaultAddress) {
        CustomerAddress address = new CustomerAddress();
        address.setAddressId(UUID.randomUUID());
        address.setAccountId(owner);
        address.setLabel(request.label());
        address.setRecipientName(request.recipientName());
        address.setRecipientPhone(request.recipientPhone());
        address.setAddressLine(request.addressLine());
        address.setWardCode(request.wardCode());
        address.setDefaultAddress(defaultAddress);
        return address;
    }

    public void patch(CustomerAddress address, AddressPatchRequest request) {
        if (request.labelPresent()) address.setLabel(request.label());
        if (request.recipientNamePresent()) address.setRecipientName(request.recipientName());
        if (request.recipientPhonePresent()) address.setRecipientPhone(request.recipientPhone());
        if (request.addressLinePresent()) address.setAddressLine(request.addressLine());
        if (request.wardCodePresent()) address.setWardCode(request.wardCode());
    }

    public AddressResponse response(CustomerAddress entity) {
        return new AddressResponse(entity.getAddressId(), entity.getLabel(), entity.getRecipientName(),
                entity.getRecipientPhone(), entity.getAddressLine(), entity.getWardCode(), entity.isDefaultAddress());
    }
}
