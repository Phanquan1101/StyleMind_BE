package com.stylemind.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.errors.ApiOperationException;
import com.stylemind.identity.dto.users.ManageUsersListRequest;
import com.stylemind.identity.validation.users.ManageUsersRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

class ManageUsersRequestValidatorTests {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ManageUsersRequestValidator validator = new ManageUsersRequestValidator();

    @Test
    void listDefaultsAndApprovedFiltersAreParsedWithoutCoercion() {
        ManageUsersListRequest defaults = validator.listCriteria(request(Map.of()));
        assertThat(defaults).isEqualTo(new ManageUsersListRequest(null, null, null, 0, 20));

        ManageUsersListRequest filtered = validator.listCriteria(request(Map.of(
                "status", new String[] {"SUSPENDED"}, "role", new String[] {"ADMIN"},
                "email_contains", new String[] {"  Example_%  "}, "page", new String[] {"2"},
                "size", new String[] {"100"})));
        assertThat(filtered).isEqualTo(new ManageUsersListRequest("SUSPENDED", "ADMIN", "Example_%", 2, 100));
    }

    @Test
    void listRejectsUnknownDuplicateMalformedAndOutOfRangeParameters() {
        for (Map<String, String[]> values : List.of(
                Map.of("account_id", new String[] {UUID.randomUUID().toString()}),
                Map.of("role", new String[] {"ADMIN", "CUSTOMER"}),
                Map.of("page", new String[] {"-1"}),
                Map.of("page", new String[] {"999999999999999999999"}),
                Map.of("size", new String[] {"101"}),
                Map.of("size", new String[] {"0"}),
                Map.of("status", new String[] {"LOCKED"}),
                Map.of("role", new String[] {"admin"}),
                Map.of("email_contains", new String[] {"   "}))) {
            assertInvalid(() -> validator.listCriteria(request(values)));
        }
    }

    @Test
    void pathIdMustUseFullHyphenatedUuid() {
        UUID expected = UUID.randomUUID();
        assertThat(validator.accountId(expected.toString())).isEqualTo(expected);
        for (String value : new String[] {"1-1-1-1-1", expected.toString().replace("-", ""),
                " " + expected, "not-a-uuid"}) {
            assertInvalid(() -> validator.accountId(value));
        }
    }

    @Test
    void statusBodyIsStrictAndDoesNotAcceptAlternateLifecycleValues() {
        assertThat(validator.requestedStatus(json("{\"account_status\":\"SUSPENDED\"}"))).isEqualTo("SUSPENDED");
        for (String value : new String[] {"{}", "null", "[]", "{\"account_status\":null}",
                "{\"account_status\":5}", "{\"account_status\":\"PENDING_VERIFICATION\"}",
                "{\"account_status\":\"LOCKED\"}",
                "{\"account_status\":\"ACTIVE\",\"account_id\":\"client-controlled\"}"}) {
            assertInvalid(() -> validator.requestedStatus(json(value)));
        }
    }

    @Test
    void rolesMustBeAnExactNonemptySetWithoutDuplicatesOrExtraProperties() {
        assertThat(validator.requestedRoles(json("{\"roles\":[\"CUSTOMER\",\"ADMIN\"]}")))
                .isEqualTo(Set.of("CUSTOMER", "ADMIN"));
        for (String value : new String[] {"{}", "null", "{\"roles\":null}", "{\"roles\":[]}",
                "{\"roles\":\"ADMIN\"}", "{\"roles\":[\"ADMIN\",\"ADMIN\"]}",
                "{\"roles\":[\"admin\"]}", "{\"roles\":[\" ADMIN\"]}",
                "{\"roles\":[5]}", "{\"roles\":[\"CUSTOMER\"],\"account_id\":\"x\"}"}) {
            assertInvalid(() -> validator.requestedRoles(json(value)));
        }
    }

    private JsonNode json(String source) {
        try {
            return mapper.readTree(source);
        } catch (IOException exception) {
            throw new IllegalArgumentException(exception);
        }
    }

    private HttpServletRequest request(Map<String, String[]> parameters) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameterMap()).thenReturn(parameters);
        return request;
    }

    private void assertInvalid(ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ApiOperationException.class)
                .satisfies(failure -> {
                    ApiOperationException exception = (ApiOperationException) failure;
                    assertThat(exception.status().value()).isEqualTo(400);
                    assertThat(exception.code()).isEqualTo("INVALID_REQUEST");
                });
    }
}
