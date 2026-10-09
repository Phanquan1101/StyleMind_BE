package com.stylemind.identity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.directory.VietnamAdministrativeDirectory;
import com.stylemind.identity.dto.AddressListResponse;
import com.stylemind.identity.dto.AddressResponse;
import com.stylemind.identity.dto.ProvinceListResponse;
import com.stylemind.identity.dto.WardListResponse;
import com.stylemind.identity.model.AddressPatch;
import com.stylemind.identity.model.AddressValues;
import com.stylemind.identity.validation.AddressRequestValidator;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ManageAddressService {
    private final AddressRequestValidator validator;
    private final ManageAddressPersistenceService persistence;
    private final VietnamAdministrativeDirectory directory;

    public ManageAddressService(AddressRequestValidator validator, ManageAddressPersistenceService persistence,
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
