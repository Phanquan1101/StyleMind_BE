package com.stylemind.wardrobecommunity.client.identity.payload;

public record AccountEmailLookupRequest(String email) {
    @Override public String toString() { return "AccountEmailLookupRequest[REDACTED]"; }
}
