package com.stylemind.identity.dto.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"account_id", "email", "profile"})
@Schema(name = "MyProfileResponse")
public record MyProfileResponse(
        @JsonProperty("account_id") UUID accountId,
        @JsonProperty("email") String email,
        @JsonProperty("profile") Profile profile) {

    @JsonInclude(JsonInclude.Include.ALWAYS)
    @JsonPropertyOrder({"display_name"})
    public record Profile(@JsonProperty("display_name") String displayName) {
    }
}
