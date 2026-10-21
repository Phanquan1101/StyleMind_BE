package com.stylemind.identity.service.address.impl;

import com.stylemind.identity.service.address.ManageAddressService;
import com.stylemind.identity.service.address.internal.ManageAddressPersistenceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.directory.VietnamAdministrativeDirectory;
import com.stylemind.identity.dto.address.AddressListResponse;
import com.stylemind.identity.dto.address.AddressResponse;
import com.stylemind.identity.dto.address.ProvinceListResponse;
import com.stylemind.identity.dto.address.WardListResponse;
import com.stylemind.identity.model.address.AddressPatch;
import com.stylemind.identity.model.address.AddressValues;
import com.stylemind.identity.validation.address.AddressRequestValidator;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManageAddressServiceImpl implements ManageAddressService {
    private final AddressRequestValidator validator;
    private final ManageAddressPersistenceService persistence;
    private final VietnamAdministrativeDirectory directory;

    public ManageAddressServiceImpl(AddressRequestValidator validator, ManageAddressPersistenceService persistence,
            VietnamAdministrativeDirectory directory) {
        this.validator = validator;
        this.persistence = persistence;
        this.directory = directory;
    }

    public AddressListResponse list(UUID owner) { return persistence.list(owner); }
    public AddressResponse get(UUID owner, UUID addressId) { return persistence.get(owner, addressId); }
    public AddressResponse create(UUID owner, JsonNode body) {
        AddressValues values = validator.validateCreate(body);
        return persistence.create(owner, UUID.randomUUID(), values);
    }
    public AddressResponse patch(UUID owner, UUID addressId, JsonNode body) {
        AddressPatch patch = validator.validatePatch(body);
        return persistence.patch(owner, addressId, patch);
    }
    public AddressResponse setDefault(UUID owner, UUID addressId) { return persistence.setDefault(owner, addressId); }
    public void delete(UUID owner, UUID addressId) { persistence.delete(owner, addressId); }
    public ProvinceListResponse provinces() { return directory.provinces(); }
    public WardListResponse wards(String provinceCode) { return directory.wards(provinceCode); }
}
