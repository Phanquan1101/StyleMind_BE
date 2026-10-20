package com.stylemind.identity.repository.address;

import com.stylemind.identity.entity.CustomerAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AddressRepository extends JpaRepository<CustomerAddress, UUID> {
    List<CustomerAddress> findByAccountIdOrderByDefaultAddressDescAddressIdAsc(UUID accountId);
    List<CustomerAddress> findByAccountId(UUID accountId);
    Optional<CustomerAddress> findByAccountIdAndAddressId(UUID accountId, UUID addressId);
}
