package com.stylemind.identity.repository;

import com.stylemind.identity.entity.CustomerAddress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByAccountIdOrderByDefaultAddressDescAddressIdAsc(UUID accountId);
    List<CustomerAddress> findByAccountId(UUID accountId);
    Optional<CustomerAddress> findByAccountIdAndAddressId(UUID accountId, UUID addressId);
}
