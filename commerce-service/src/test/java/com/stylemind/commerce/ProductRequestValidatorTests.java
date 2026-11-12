package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.validation.product.ProductRequestValidator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ProductRequestValidatorTests {

    private final ProductRequestValidator validator = new ProductRequestValidator();

    @Test
    void publicListAcceptsSupportedPriceSorts() {
        MockHttpServletRequest ascending = new MockHttpServletRequest();
        ascending.addParameter("sort", "price_asc");
        assertThat(validator.listCriteria(ascending).sort()).isEqualTo("price_asc");

        MockHttpServletRequest descending = new MockHttpServletRequest();
        descending.addParameter("sort", "price_desc");
        assertThat(validator.listCriteria(descending).sort()).isEqualTo("price_desc");
    }

    @Test
    void listRejectsUnknownOrAdminPriceSorts() {
        MockHttpServletRequest unknown = new MockHttpServletRequest();
        unknown.addParameter("sort", "newest");
        assertThatThrownBy(() -> validator.listCriteria(unknown)).isInstanceOf(ApiOperationException.class);

        MockHttpServletRequest admin = new MockHttpServletRequest();
        admin.addParameter("sort", "price_asc");
        assertThatThrownBy(() -> validator.adminListCriteria(admin)).isInstanceOf(ApiOperationException.class);
    }
}
