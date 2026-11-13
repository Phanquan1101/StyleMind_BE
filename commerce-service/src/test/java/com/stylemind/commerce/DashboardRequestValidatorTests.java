package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.validation.dashboard.DashboardRequestValidator;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class DashboardRequestValidatorTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private final DashboardRequestValidator validator = new DashboardRequestValidator();

    private MockHttpServletRequest request(String... pairs) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        for (int index = 0; index < pairs.length; index += 2) {
            request.addParameter(pairs[index], pairs[index + 1]);
        }
        return request;
    }

    @Test
    void defaultsToTheLastThirtyDaysEndingToday() {
        var range = validator.range(request(), TODAY);
        assertThat(range.to()).isEqualTo(TODAY);
        assertThat(range.from()).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(range.days()).isEqualTo(30);
    }

    @Test
    void acceptsAnInclusiveRangeIncludingASingleDay() {
        assertThat(validator.range(request("from", "2026-11-13", "to", "2026-11-13"), TODAY).days()).isEqualTo(1);
        assertThat(validator.range(request("from", "2026-11-07", "to", "2026-11-13"), TODAY).days()).isEqualTo(7);
        assertThat(validator.range(request("from", "2025-11-13", "to", "2026-11-13"), TODAY).days()).isEqualTo(366);
    }

    @Test
    void rejectsMalformedHalfGivenReversedFutureOversizedAndUnknownParameters() {
        String[][] bad = {
            {"from", "13/11/2026", "to", "2026-11-13"},
            {"from", "2026-11-13"},
            {"to", "2026-11-13"},
            {"from", "2026-11-14", "to", "2026-11-13"},
            {"from", "2026-11-10", "to", "2026-11-14"},
            {"from", "2025-11-12", "to", "2026-11-13"},
            {"from", "", "to", "2026-11-13"},
            {"foo", "bar"},
        };
        for (String[] pairs : bad) {
            assertThatThrownBy(() -> validator.range(request(pairs), TODAY))
                    .as(String.join(",", pairs))
                    .isInstanceOfSatisfying(ApiOperationException.class, exception -> {
                        assertThat(exception.status().value()).isEqualTo(400);
                        assertThat(exception.code()).isEqualTo("INVALID_REQUEST");
                        assertThat(exception.fieldErrors()).isNotEmpty();
                    });
        }
    }

    @Test
    void rejectsRepeatedParameters() {
        MockHttpServletRequest request = request("from", "2026-11-01");
        request.addParameter("from", "2026-11-02");
        request.addParameter("to", "2026-11-13");
        assertThatThrownBy(() -> validator.range(request, TODAY)).isInstanceOf(ApiOperationException.class);
    }
}
