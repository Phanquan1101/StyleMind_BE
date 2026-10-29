package com.stylemind.identity.service;

import com.stylemind.identity.dto.address.AddressCreateRequest;
import com.stylemind.identity.dto.address.AddressListResponse;
import com.stylemind.identity.dto.address.AddressPatchRequest;
import com.stylemind.identity.dto.address.AddressResponse;
import com.stylemind.identity.dto.address.ProvinceListResponse;
import com.stylemind.identity.dto.address.WardListResponse;
import java.util.UUID;

public interface AddressService {
    AddressListResponse list(UUID owner);
    AddressResponse get(UUID owner, UUID addressId);
    AddressResponse create(UUID owner, AddressCreateRequest request);
    AddressResponse patch(UUID owner, UUID addressId, AddressPatchRequest request);
    AddressResponse setDefault(UUID owner, UUID addressId);
    void delete(UUID owner, UUID addressId);
    ProvinceListResponse provinces();
    WardListResponse wards(String provinceCode);
}
