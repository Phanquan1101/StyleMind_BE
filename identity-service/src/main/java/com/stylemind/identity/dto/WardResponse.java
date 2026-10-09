package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record WardResponse(@JsonProperty("code") String code, @JsonProperty("name") String name) {
}
