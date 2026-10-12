package com.stylemind.identity.dto.profile;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PatchMyProfileRequest {

    private JsonNode displayName;
    private boolean displayNamePresent;
    private final Set<String> unknownProperties = new LinkedHashSet<>();

    @JsonSetter(value = "display_name", nulls = Nulls.SET)
    public void setDisplayName(JsonNode displayName) {
        this.displayNamePresent = true;
        this.displayName = displayName;
    }

    @JsonProperty("display_name")
    @Schema(type = "string", description = "Required non-null display name; 1–100 Unicode code points after trim.")
    public JsonNode getDisplayName() {
        return displayName;
    }

    @JsonAnySetter
    public void addUnknownProperty(String name, JsonNode value) {
        unknownProperties.add(name);
    }

    @JsonIgnore
    public boolean isDisplayNamePresent() {
        return displayNamePresent;
    }

    @JsonIgnore
    public Set<String> getUnknownProperties() {
        return Set.copyOf(unknownProperties);
    }
}
