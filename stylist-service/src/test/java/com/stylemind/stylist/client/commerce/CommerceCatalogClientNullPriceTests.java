package com.stylemind.stylist.client.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantBatchLookupResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CommerceCatalogClientNullPriceTests {
    private static final UUID PRODUCT_ID = UUID.fromString("60000000-0000-4000-8000-000000000001");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("60000000-0000-4000-8000-000000000002");
    private static final UUID VARIANT_ID = UUID.fromString("70000000-0000-4000-8000-000000000001");
    private static final UUID SECOND_VARIANT_ID = UUID.fromString("70000000-0000-4000-8000-000000000002");
    private final CommerceCatalogFeignClient feign = mock(CommerceCatalogFeignClient.class);
    private final CommerceCatalogClient client = new CommerceCatalogClient(feign);

    @AfterEach
    void stopClientExecutor() {
        client.stopProductExecutor();
    }

    @Test
    void nullBatchPriceIsProtocolErrorBeforeProductHydration() {
        when(feign.lookupActiveVariants(any())).thenReturn(new VariantBatchLookupResponse(List.of(
                variant(VARIANT_ID, PRODUCT_ID, null))));

        assertProtocolError(() -> client.hydrate(Map.of(VARIANT_ID, PRODUCT_ID)));

        verify(feign, never()).getProduct(any());
    }

    @Test
    void oneNullPriceInBatchRejectsWholeBatchBeforeAnyProductHydration() {
        when(feign.lookupActiveVariants(any())).thenReturn(new VariantBatchLookupResponse(List.of(
                variant(VARIANT_ID, PRODUCT_ID, BigDecimal.valueOf(100)),
                variant(SECOND_VARIANT_ID, SECOND_PRODUCT_ID, null))));

        assertProtocolError(() -> client.hydrate(Map.of(
                VARIANT_ID, PRODUCT_ID, SECOND_VARIANT_ID, SECOND_PRODUCT_ID)));

        verify(feign, never()).getProduct(any());
    }

    @Test
    void nullProductDetailPriceIsProtocolError() {
        when(feign.lookupActiveVariants(any())).thenReturn(new VariantBatchLookupResponse(List.of(
                variant(VARIANT_ID, PRODUCT_ID, BigDecimal.valueOf(100)))));
        when(feign.getProduct(PRODUCT_ID)).thenReturn(product(variant(VARIANT_ID, PRODUCT_ID, null)));

        assertProtocolError(() -> client.hydrate(Map.of(VARIANT_ID, PRODUCT_ID)));
    }

    @Test
    void exactNonNegativeWholeVndPricesRemainAccepted() {
        assertThat(hydrate(BigDecimal.ZERO)).containsKey(VARIANT_ID);
        assertThat(hydrate(BigDecimal.valueOf(123_456))).containsKey(VARIANT_ID);
    }

    @Test
    void negativeFractionalAndOverflowPricesRemainProtocolErrors() {
        assertProtocolError(() -> hydrate(BigDecimal.valueOf(-1)));
        assertProtocolError(() -> hydrate(new BigDecimal("1.5")));
        assertProtocolError(() -> hydrate(new BigDecimal("9223372036854775808")));
    }

    private Map<UUID, CommerceCatalogClient.HydratedVariant> hydrate(BigDecimal price) {
        when(feign.lookupActiveVariants(any())).thenReturn(new VariantBatchLookupResponse(List.of(
                variant(VARIANT_ID, PRODUCT_ID, price))));
        when(feign.getProduct(PRODUCT_ID)).thenReturn(product(variant(VARIANT_ID, PRODUCT_ID, price)));
        return client.hydrate(Map.of(VARIANT_ID, PRODUCT_ID), Duration.ofSeconds(2));
    }

    private static ProductResponse product(VariantResponse detailVariant) {
        return new ProductResponse(PRODUCT_ID, UUID.fromString("40000000-0000-4000-8000-000000000001"),
                UUID.fromString("50000000-0000-4000-8000-000000000001"), "Áo", "StyleMind", "Áo sơ mi",
                null, "WOMEN", "ACTIVE", List.of(detailVariant));
    }

    private static VariantResponse variant(UUID variantId, UUID productId, BigDecimal price) {
        return new VariantResponse(variantId, productId, "SKU-" + variantId, "M", "blue", "cotton",
                price, "ACTIVE", List.of());
    }

    private static void assertProtocolError(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOf(StylistApiException.class)
                .satisfies(exception -> assertThat(((StylistApiException) exception).errorCode())
                        .isEqualTo(ErrorCode.DEPENDENCY_PROTOCOL_ERROR));
    }
}
