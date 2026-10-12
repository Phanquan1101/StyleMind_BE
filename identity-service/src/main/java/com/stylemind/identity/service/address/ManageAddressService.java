package com.stylemind.identity.service.address;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.dto.address.AddressListResponse;
import com.stylemind.identity.dto.address.AddressResponse;
import com.stylemind.identity.dto.address.ProvinceListResponse;
import com.stylemind.identity.dto.address.WardListResponse;
import java.util.UUID;

public interface ManageAddressService {
    AddressListResponse list(UUID owner);
    AddressResponse get(UUID owner, UUID addressId);
    AddressResponse create(UUID owner, JsonNode body);
    AddressResponse patch(UUID owner, UUID addressId, JsonNode body);
    AddressResponse setDefault(UUID owner, UUID addressId);
    void delete(UUID owner, UUID addressId);
    ProvinceListResponse provinces();
    WardListResponse wards(String provinceCode);
}
