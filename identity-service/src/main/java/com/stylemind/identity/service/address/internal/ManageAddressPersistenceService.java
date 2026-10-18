package com.stylemind.identity.service.address.internal;

import com.stylemind.identity.dto.address.AddressListResponse;
import com.stylemind.identity.dto.address.AddressResponse;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.CustomerAddress;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.model.address.AddressPatch;
import com.stylemind.identity.model.address.AddressSnapshot;
import com.stylemind.identity.model.address.AddressValues;
import com.stylemind.identity.repository.account.AccountRepository;
import com.stylemind.identity.repository.address.AddressRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManageAddressPersistenceService {

    private final AccountRepository accountRepository;
    private final AddressRepository addressRepository;

    public ManageAddressPersistenceService(AccountRepository accountRepository, AddressRepository addressRepository) {
        this.accountRepository = accountRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public AddressListResponse list(UUID owner) {
        requireAccount(owner);
        return new AddressListResponse(addressRepository
                .findByAccountIdOrderByDefaultAddressDescAddressIdAsc(owner).stream()
                .map(ManageAddressPersistenceService::snapshot).map(AddressResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID owner, UUID addressId) {
        requireAccount(owner);
        return AddressResponse.from(addressRepository.findByAccountIdAndAddressId(owner, addressId)
                .map(ManageAddressPersistenceService::snapshot).orElseThrow(this::notFound));
    }

    @Transactional
    public AddressResponse create(UUID owner, UUID addressId, AddressValues values) {
        lockOwner(owner);
        List<CustomerAddress> before = checkedState(owner);
        CustomerAddress address = new CustomerAddress();
        address.setAddressId(addressId);
        address.setAccountId(owner);
        apply(values, address);
        address.setDefaultAddress(before.isEmpty());
        return AddressResponse.from(snapshot(addressRepository.saveAndFlush(address)));
    }

    @Transactional
    public AddressResponse patch(UUID owner, UUID addressId, AddressPatch patch) {
        lockOwner(owner);
        CustomerAddress address = checkedState(owner).stream()
                .filter(value -> value.getAddressId().equals(addressId)).findFirst().orElseThrow(this::notFound);
        AddressValues merged = new AddressValues(
                patch.labelPresent() ? patch.label() : address.getLabel(),
                patch.recipientNamePresent() ? patch.recipientName() : address.getRecipientName(),
                patch.recipientPhonePresent() ? patch.recipientPhone() : address.getRecipientPhone(),
                patch.addressLinePresent() ? patch.addressLine() : address.getAddressLine(),
                patch.wardCodePresent() ? patch.wardCode() : address.getWardCode());
        apply(merged, address);
        return AddressResponse.from(snapshot(addressRepository.saveAndFlush(address)));
    }

    @Transactional
    public AddressResponse setDefault(UUID owner, UUID addressId) {
        lockOwner(owner);
        List<CustomerAddress> addresses = checkedState(owner);
        CustomerAddress target = addresses.stream().filter(value -> value.getAddressId().equals(addressId))
                .findFirst().orElseThrow(this::notFound);
        if (!target.isDefaultAddress()) {
            addresses.forEach(address -> address.setDefaultAddress(address == target));
            addressRepository.saveAll(addresses);
            addressRepository.flush();
        }
        return AddressResponse.from(snapshot(target));
    }

    @Transactional
    public void delete(UUID owner, UUID addressId) {
        lockOwner(owner);
        List<CustomerAddress> addresses = checkedState(owner);
        CustomerAddress target = addresses.stream().filter(value -> value.getAddressId().equals(addressId))
                .findFirst().orElseThrow(this::notFound);
        if (target.isDefaultAddress() && addresses.size() > 1) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "DEFAULT_ADDRESS_CHANGE_REQUIRED",
                    "Choose another default address before deleting this address", 0);
        }
        addressRepository.delete(target);
        addressRepository.flush();
    }

    private List<CustomerAddress> checkedState(UUID owner) {
        List<CustomerAddress> values = addressRepository.findByAccountId(owner);
        long defaults = values.stream().filter(CustomerAddress::isDefaultAddress).count();
        if ((!values.isEmpty() && defaults != 1) || (values.isEmpty() && defaults != 0)) {
            throw new IllegalStateException("Pre-existing address default invariant is invalid");
        }
        return values;
    }

    private void lockOwner(UUID owner) {
        if (accountRepository.findLockedByAccountId(owner).isEmpty()) {
            throw notFound();
        }
    }

    private void requireAccount(UUID owner) {
        if (!accountRepository.existsById(owner)) {
            throw notFound();
        }
    }

    private static void apply(AddressValues values, CustomerAddress entity) {
        entity.setLabel(values.label());
        entity.setRecipientName(values.recipientName());
        entity.setRecipientPhone(values.recipientPhone());
        entity.setAddressLine(values.addressLine());
        entity.setWardCode(values.wardCode());
    }

    private static AddressSnapshot snapshot(CustomerAddress entity) {
        return new AddressSnapshot(entity.getAddressId(), entity.getLabel(), entity.getRecipientName(),
                entity.getRecipientPhone(), entity.getAddressLine(), entity.getWardCode(), entity.isDefaultAddress());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", 0);
    }
}
