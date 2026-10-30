package com.stylemind.identity.dto.internal;

public record AccountEmailLookupRequest(
        @jakarta.validation.constraints.Email @jakarta.validation.constraints.NotBlank
        @jakarta.validation.constraints.Size(max = 254) String email) {
    @Override public String toString() { return "AccountEmailLookupRequest[REDACTED]"; }
}
