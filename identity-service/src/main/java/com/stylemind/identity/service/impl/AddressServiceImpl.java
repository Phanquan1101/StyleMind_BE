package com.stylemind.identity.service.impl;

import com.stylemind.identity.directory.VietnamAdministrativeDirectory;
import com.stylemind.identity.dto.address.AddressCreateRequest;
import com.stylemind.identity.dto.address.AddressListResponse;
import com.stylemind.identity.dto.address.AddressPatchRequest;
import com.stylemind.identity.dto.address.AddressResponse;
import com.stylemind.identity.dto.address.ProvinceListResponse;
import com.stylemind.identity.dto.address.WardListResponse;
import com.stylemind.identity.entity.CustomerAddress;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.errors.ErrorCode;
import com.stylemind.identity.mapper.AddressMapper;
import com.stylemind.identity.repository.AccountRepository;
import com.stylemind.identity.repository.AddressRepository;
import com.stylemind.identity.service.AddressService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AddressServiceImpl implements AddressService {

    private final AccountRepository accountRepository;
    private final AddressRepository addressRepository;
    private final VietnamAdministrativeDirectory directory;
    private final AddressMapper mapper;

    public AddressServiceImpl(AccountRepository accountRepository, AddressRepository addressRepository,
            VietnamAdministrativeDirectory directory, AddressMapper mapper) {
        this.accountRepository = accountRepository;
        this.addressRepository = addressRepository;
        this.directory = directory;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public AddressListResponse list(UUID owner) {
        requireAccount(owner);
        return new AddressListResponse(addressRepository
                .findByAccountIdOrderByDefaultAddressDescAddressIdAsc(owner).stream()
                .map(mapper::response).toList());
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID owner, UUID addressId) {
        requireAccount(owner);
        return addressRepository.findByAccountIdAndAddressId(owner, addressId)
                .map(mapper::response).orElseThrow(() -> new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public AddressResponse create(UUID owner, AddressCreateRequest request) {
        lockOwner(owner);
        List<CustomerAddress> before = checkedState(owner);
        CustomerAddress address = mapper.create(owner, request, before.isEmpty());
        return mapper.response(addressRepository.saveAndFlush(address));
    }

    @Transactional
    public AddressResponse patch(UUID owner, UUID addressId, AddressPatchRequest request) {
        lockOwner(owner);
        CustomerAddress address = checkedState(owner).stream()
                .filter(value -> value.getAddressId().equals(addressId)).findFirst()
                .orElseThrow(() -> new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND));
        mapper.patch(address, request);
        return mapper.response(addressRepository.saveAndFlush(address));
    }

    @Transactional
    public AddressResponse setDefault(UUID owner, UUID addressId) {
        lockOwner(owner);
        List<CustomerAddress> addresses = checkedState(owner);
        CustomerAddress target = addresses.stream().filter(value -> value.getAddressId().equals(addressId))
                .findFirst().orElseThrow(() -> new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!target.isDefaultAddress()) {
            addresses.forEach(address -> address.setDefaultAddress(address == target));
            addressRepository.saveAll(addresses);
            addressRepository.flush();
        }
        return mapper.response(target);
    }

    @Transactional
    public void delete(UUID owner, UUID addressId) {
        lockOwner(owner);
        List<CustomerAddress> addresses = checkedState(owner);
        CustomerAddress target = addresses.stream().filter(value -> value.getAddressId().equals(addressId))
                .findFirst().orElseThrow(() -> new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND));
        if (target.isDefaultAddress() && addresses.size() > 1) {
            throw new ApiOperationException(ErrorCode.DEFAULT_ADDRESS_CHANGE_REQUIRED);
        }
        addressRepository.delete(target);
        addressRepository.flush();
    }

    public ProvinceListResponse provinces() {
        return directory.provinces();
    }

    public WardListResponse wards(String provinceCode) {
        return directory.wards(provinceCode);
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
            throw new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireAccount(UUID owner) {
        if (!accountRepository.existsById(owner)) {
            throw new ApiOperationException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }
}
