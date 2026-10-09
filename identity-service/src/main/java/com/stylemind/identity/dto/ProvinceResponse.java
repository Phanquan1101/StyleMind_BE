package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ProvinceResponse(@JsonProperty("code") String code, @JsonProperty("name") String name) {
}
