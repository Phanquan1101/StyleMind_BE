package com.stylemind.wardrobecommunity.client.identity.payload;

import java.util.List;

public record AccountLookupResponse(
        List<AccountLookupItem> items) {
}
