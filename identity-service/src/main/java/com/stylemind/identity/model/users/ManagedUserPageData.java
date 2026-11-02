package com.stylemind.identity.model.users;

import java.util.List;

public record ManagedUserPageData(long totalElements, List<ManagedUserSummary> items) {

    public ManagedUserPageData {
        items = List.copyOf(items);
    }
}
