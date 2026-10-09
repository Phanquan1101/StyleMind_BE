package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record AddressListResponse(@JsonProperty("addresses") List<AddressResponse> addresses) {
}
