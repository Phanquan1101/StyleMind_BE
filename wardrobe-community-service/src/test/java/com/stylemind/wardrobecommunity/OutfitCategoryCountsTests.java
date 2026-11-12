package com.stylemind.wardrobecommunity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stylemind.wardrobecommunity.client.commerce.CommerceCatalogClient;
import com.stylemind.wardrobecommunity.client.commerce.payload.VariantLookupItem;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListCriteria;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitSummaryResponse;
import com.stylemind.wardrobecommunity.entity.Outfit;
import com.stylemind.wardrobecommunity.entity.OutfitItem;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitItemRepository;
import com.stylemind.wardrobecommunity.repository.outfit.OutfitRepository;
import com.stylemind.wardrobecommunity.service.outfit.OutfitService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.support.TransactionTemplate;

/** Outfit list: category counts come from one Commerce lookup per page and never break the list. */
class OutfitCategoryCountsTests {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SHIRTS = UUID.randomUUID();
    private static final UUID PANTS = UUID.randomUUID();

    private final OutfitRepository outfits = mock(OutfitRepository.class);
    private final OutfitItemRepository outfitItems = mock(OutfitItemRepository.class);
    private final CommerceCatalogClient catalog = mock(CommerceCatalogClient.class);
    private final OutfitService service = new OutfitService(outfits, outfitItems, catalog, mock(TransactionTemplate.class));

    private Outfit outfit(String name) {
        Outfit outfit = new Outfit();
        outfit.setOutfitId(UUID.randomUUID());
        outfit.setOwnerAccountId(OWNER);
        outfit.setName(name);
        outfit.setCreationSource("MANUAL");
        outfit.setVisibility("PRIVATE");
        outfit.setCreatedAt(OffsetDateTime.now());
        outfit.setUpdatedAt(OffsetDateTime.now());
        return outfit;
    }

    private OutfitItem item(Outfit outfit, UUID variantId, int order) {
        OutfitItem item = new OutfitItem();
        item.setOutfitItemId(UUID.randomUUID());
        item.setOutfitId(outfit.getOutfitId());
        item.setVariantId(variantId);
        item.setDisplayOrder(order);
        return item;
    }

    private VariantLookupItem variant(UUID id, UUID categoryId, String categoryName) {
        return new VariantLookupItem(id, UUID.randomUUID(), "P", "ACTIVE", "ACTIVE", "Den", "M", 1000L, null, true,
                categoryId, categoryName);
    }

    private List<Object[]> pageOf(Outfit first, Outfit second) {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[] {first, 3L});
        rows.add(new Object[] {second, 1L});
        return rows;
    }

    @Test
    void listCarriesCategoryCountsFromASingleLookup() throws Exception {
        Outfit first = outfit("A");
        Outfit second = outfit("B");
        UUID shirt1 = UUID.randomUUID();
        UUID shirt2 = UUID.randomUUID();
        UUID pants = UUID.randomUUID();
        UUID gone = UUID.randomUUID();
        when(outfits.countByOwnerAccountId(OWNER)).thenReturn(2L);
        when(outfits.findPageWithItemCount(any(), any())).thenReturn(pageOf(first, second));
        when(outfitItems.findByOutfitIdIn(anyCollection())).thenReturn(List.of(
                item(first, shirt1, 0), item(first, shirt2, 1), item(first, pants, 2), item(second, gone, 0)));
        when(catalog.lookup(anyCollection())).thenReturn(Map.of(
                shirt1, variant(shirt1, SHIRTS, "Ao"),
                shirt2, variant(shirt2, SHIRTS, "Ao"),
                pants, variant(pants, PANTS, "Quan")));

        OutfitListResponse result = service.list(OWNER, new OutfitListCriteria(0, 20));

        OutfitSummaryResponse a = result.items().get(0);
        assertThat(a.categoryCounts()).extracting("categoryName", "count").containsExactly(
                org.assertj.core.groups.Tuple.tuple("Ao", 2L), org.assertj.core.groups.Tuple.tuple("Quan", 1L));
        // A variant Commerce no longer returns is simply not counted.
        assertThat(result.items().get(1).categoryCounts()).isEmpty();
        org.mockito.Mockito.verify(catalog, org.mockito.Mockito.times(1)).lookup(anyCollection());

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        assertThat(mapper.writeValueAsString(a)).contains("\"category_counts\"").contains("\"category_name\":\"Ao\"");
    }

    @Test
    void listStillWorksWhenCommerceFailsAndOmitsTheField() throws Exception {
        Outfit first = outfit("A");
        Outfit second = outfit("B");
        when(outfits.countByOwnerAccountId(OWNER)).thenReturn(2L);
        when(outfits.findPageWithItemCount(any(), any())).thenReturn(pageOf(first, second));
        when(outfitItems.findByOutfitIdIn(anyCollection())).thenReturn(List.of(item(first, UUID.randomUUID(), 0)));
        when(catalog.lookup(anyCollection())).thenThrow(new ApiOperationException(HttpStatus.SERVICE_UNAVAILABLE,
                "CATALOG_UNAVAILABLE", "Product catalog is temporarily unavailable"));

        OutfitListResponse result = service.list(OWNER, new OutfitListCriteria(0, 20));

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).itemCount()).isEqualTo(3L);
        assertThat(result.items().get(0).categoryCounts()).isNull();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        assertThat(mapper.writeValueAsString(result.items().get(0))).doesNotContain("category_counts");
    }
}
