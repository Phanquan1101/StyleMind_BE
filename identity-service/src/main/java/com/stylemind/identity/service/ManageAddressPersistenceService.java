package com.stylemind.identity.service;

import com.stylemind.identity.dto.AddressListResponse;
import com.stylemind.identity.dto.AddressResponse;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.AddressPatch;
import com.stylemind.identity.model.AddressSnapshot;
import com.stylemind.identity.model.AddressValues;
import com.stylemind.identity.repository.AddressRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageAddressPersistenceService {
    private final AddressRepository repository;

    public ManageAddressPersistenceService(AddressRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public AddressListResponse list(UUID owner) {
        requireAccount(owner);
        return new AddressListResponse(repository.findAll(owner).stream().map(AddressResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID owner, UUID addressId) {
        requireAccount(owner);
        return AddressResponse.from(repository.findOne(owner, addressId).orElseThrow(this::notFound));
    }

    @Transactional
    public AddressResponse create(UUID owner, UUID addressId, AddressValues values) {
        lockOwner(owner);
        List<AddressSnapshot> before = checkedState(owner);
        boolean first = before.isEmpty();
        repository.insert(addressId, owner, values, first);
        return AddressResponse.from(repository.findOne(owner, addressId).orElseThrow());
    }

    @Transactional
    public AddressResponse patch(UUID owner, UUID addressId, AddressPatch patch) {
        lockOwner(owner);
        List<AddressSnapshot> before = checkedState(owner);
        AddressSnapshot current = before.stream().filter(address -> address.addressId().equals(addressId))
                .findFirst().orElseThrow(this::notFound);
        AddressValues merged = new AddressValues(
                patch.labelPresent() ? patch.label() : current.label(),
                patch.recipientNamePresent() ? patch.recipientName() : current.recipientName(),
                patch.recipientPhonePresent() ? patch.recipientPhone() : current.recipientPhone(),
                patch.addressLinePresent() ? patch.addressLine() : current.addressLine(),
                patch.wardCodePresent() ? patch.wardCode() : current.wardCode());
        repository.update(owner, addressId, merged);
        return AddressResponse.from(repository.findOne(owner, addressId).orElseThrow());
    }

    @Transactional
    public AddressResponse setDefault(UUID owner, UUID addressId) {
        lockOwner(owner);
        List<AddressSnapshot> before = checkedState(owner);
        AddressSnapshot target = before.stream().filter(address -> address.addressId().equals(addressId))
                .findFirst().orElseThrow(this::notFound);
        if (!target.isDefault()) {
            repository.clearDefaults(owner);
            repository.setDefault(owner, addressId);
        }
        return AddressResponse.from(repository.findOne(owner, addressId).orElseThrow());
    }

    @Transactional
    public void delete(UUID owner, UUID addressId) {
        lockOwner(owner);
        List<AddressSnapshot> before = checkedState(owner);
        AddressSnapshot target = before.stream().filter(address -> address.addressId().equals(addressId))
                .findFirst().orElseThrow(this::notFound);
        if (target.isDefault() && before.size() > 1) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "DEFAULT_ADDRESS_CHANGE_REQUIRED",
                    "Choose another default address before deleting this address", 0);
        }
        repository.delete(owner, addressId);
    }

    private List<AddressSnapshot> checkedState(UUID owner) {
        List<AddressSnapshot> values = repository.findAll(owner);
        long defaults = values.stream().filter(AddressSnapshot::isDefault).count();
        if ((!values.isEmpty() && defaults != 1) || (values.isEmpty() && defaults != 0)) {
            throw new IllegalStateException("Pre-existing address default invariant is invalid");
        }
        return values;
    }

    private void lockOwner(UUID owner) {
        if (!repository.lockAccount(owner)) throw notFound();
    }

    private void requireAccount(UUID owner) {
        if (!repository.accountExists(owner)) throw notFound();
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", 0);
    }
}
