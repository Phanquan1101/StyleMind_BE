package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.wardrobecommunity.model.friendship.FriendshipPair;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FriendshipPairTests {

    @Test
    void reversingAccountsKeepsTheSamePair() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertThat(new FriendshipPair(first, second)).isEqualTo(new FriendshipPair(second, first));
    }

    @Test
    void differentPeersNeverBecomeTheSamePair() {
        UUID first = new UUID(0, 1);
        UUID second = new UUID(0, 2);
        UUID third = new UUID(0, 3);

        assertThat(new FriendshipPair(first, second)).isNotEqualTo(new FriendshipPair(first, third));
    }

    @Test
    void matchesPostgresOrderAcrossMostSignificantSignBit() {
        UUID low = UUID.fromString("7fffffff-ffff-ffff-ffff-ffffffffffff");
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");

        FriendshipPair pair = new FriendshipPair(high, low);

        assertThat(pair.accountLowId()).isEqualTo(low);
        assertThat(pair.accountHighId()).isEqualTo(high);
    }

    @Test
    void matchesPostgresOrderAcrossLeastSignificantSignBit() {
        UUID low = UUID.fromString("00000000-0000-0001-7fff-ffffffffffff");
        UUID high = UUID.fromString("00000000-0000-0001-8000-000000000000");

        FriendshipPair pair = new FriendshipPair(high, low);

        assertThat(pair.accountLowId()).isEqualTo(low);
        assertThat(pair.accountHighId()).isEqualTo(high);
    }

    @Test
    void selfFriendshipIsRejected() {
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() -> new FriendshipPair(account, account))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingAccountIsRejectedInEitherPosition() {
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() -> new FriendshipPair(null, account)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FriendshipPair(account, null)).isInstanceOf(NullPointerException.class);
    }
}
