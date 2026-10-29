package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import com.stylemind.identity.entity.Account;
import com.stylemind.identity.entity.StyleProfile;
import com.stylemind.identity.entity.UserProfile;
import com.stylemind.identity.mapper.ProfileMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileMapperTests {

    private final ProfileMapper mapper = new ProfileMapper();

    @Test
    void mapsProfileDtosAndUpdatesEntities() {
        UUID accountId = UUID.randomUUID();
        Account account = new Account();
        account.setAccountId(accountId);
        account.setEmail("customer@example.test");
        account.setPasswordHash("must-not-be-exposed");
        UserProfile userProfile = mapper.newUserProfile(accountId);
        mapper.updateUserProfile(userProfile, "Sam");

        assertThat(userProfile.getAccountId()).isEqualTo(accountId);
        assertThat(mapper.myProfile(account, userProfile).profile().displayName()).isEqualTo("Sam");
        assertThat(mapper.myProfile(account, null).profile()).isNull();

        StyleProfileUpdateRequest request = new StyleProfileUpdateRequest("REGULAR", new BigDecimal("170.0"),
                new BigDecimal("62.5"), "M", "30", "40", List.of("CASUAL"), List.of("BLACK"), List.of());
        StyleProfile styleProfile = mapper.newStyleProfile(accountId);
        mapper.updateStyleProfile(styleProfile, request);

        assertThat(styleProfile.getAccountId()).isEqualTo(accountId);
        assertThat(mapper.styleProfile(styleProfile, request.styleCodes(), request.colorCodes(), request.brandIds())
                .styleProfile().fitPreference()).isEqualTo("REGULAR");
    }
}
