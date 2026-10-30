package com.stylemind.wardrobecommunity.model.friendship;

import java.util.Objects;
import java.util.UUID;

/** An unordered pair, stored in the same unsigned UUID order PostgreSQL uses. */
public record FriendshipPair(UUID accountLowId, UUID accountHighId) {

    public FriendshipPair {
        Objects.requireNonNull(accountLowId, "First account is required");
        Objects.requireNonNull(accountHighId, "Second account is required");
        int order = compare(accountLowId, accountHighId);
        if (order == 0) {
            throw new IllegalArgumentException("A friendship requires two different accounts");
        }
        if (order > 0) {
            UUID previousLow = accountLowId;
            accountLowId = accountHighId;
            accountHighId = previousLow;
        }
    }

    private static int compare(UUID left, UUID right) {
        // UUID.compareTo uses signed longs and differs from PostgreSQL across the sign bit.
        int mostSignificant = Long.compareUnsigned(left.getMostSignificantBits(), right.getMostSignificantBits());
        return mostSignificant != 0 ? mostSignificant
                : Long.compareUnsigned(left.getLeastSignificantBits(), right.getLeastSignificantBits());
    }
}
