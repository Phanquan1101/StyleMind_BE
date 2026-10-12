package com.stylemind.identity.dto.address;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProvinceResponse(@JsonProperty("code") String code, @JsonProperty("name") String name) {
}
