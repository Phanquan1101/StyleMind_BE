package com.stylemind.identity.dto.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AddressListResponse(@JsonProperty("addresses") List<AddressResponse> addresses) {
}
