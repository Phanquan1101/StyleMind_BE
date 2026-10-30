package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.wardrobecommunity.dto.friendship.FriendPageCriteria;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.validation.friendship.FriendshipRequestValidator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

class FriendshipRequestValidatorTests {

    private final FriendshipRequestValidator validator = new FriendshipRequestValidator();

    @Test
    void defaultsAndBoundsFollowTheExistingPaginationConvention() {
        assertThat(validator.list(new MockHttpServletRequest())).isEqualTo(new FriendPageCriteria(0, 20));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("page", "2147483647");
        request.addParameter("size", "100");
        assertThat(validator.list(request)).isEqualTo(new FriendPageCriteria(Integer.MAX_VALUE, 100));
    }

    @Test
    void malformedNegativeOverflowAndOutOfRangeNumbersAreRejected() {
        for (String[] input : new String[][] {
                {"page", "-1"}, {"page", "2147483648"}, {"page", "1.5"}, {"page", ""},
                {"size", "0"}, {"size", "101"}, {"size", "-1"}, {"size", "abc"}}) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addParameter(input[0], input[1]);
            invalid(() -> validator.list(request));
        }
    }

    @Test
    void duplicateAndUnknownQueryParametersCannotOverrideTheActor() {
        MockHttpServletRequest duplicate = new MockHttpServletRequest();
        duplicate.addParameter("page", "0", "1");
        invalid(() -> validator.list(duplicate));
        for (String field : new String[] {"owner_id", "account_id", "sender_account_id", "direction", "sort"}) {
            MockHttpServletRequest unknown = new MockHttpServletRequest();
            unknown.addParameter(field, "other");
            invalid(() -> validator.list(unknown));
        }
    }

    @Test
    void pathRequiresAFullHyphenatedUuidAndAcceptsHexCase() {
        UUID expected = UUID.fromString("abcdefab-0000-0000-0000-000000000001");
        assertThat(validator.friendAccountId("ABCDEFAB-0000-0000-0000-000000000001")).isEqualTo(expected);
        for (String invalid : new String[] {null, "", "1-1-1-1-1", "not-a-uuid", " " + expected, expected + " "}) {
            invalid(() -> validator.friendAccountId(invalid));
        }
    }

    @Test
    void unfriendRejectsEveryQueryParameter() {
        validator.noQuery(new MockHttpServletRequest());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("owner_id", UUID.randomUUID().toString());
        invalid(() -> validator.noQuery(request));
    }

    private void invalid(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiOperationException.class, exception -> {
            assertThat(exception.status()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(exception.code()).isEqualTo("INVALID_REQUEST");
        });
    }
}
