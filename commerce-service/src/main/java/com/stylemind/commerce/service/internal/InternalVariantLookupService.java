package com.stylemind.commerce.service.internal;

import com.stylemind.commerce.dto.internal.InternalVariantItem;
import com.stylemind.commerce.dto.internal.InternalVariantLookupResponse;
import com.stylemind.commerce.repository.product.ProductVariantRepository;
import java.util.Collection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InternalVariantLookupService {

    private final ProductVariantRepository variants;

    @Transactional(readOnly = true)
    public InternalVariantLookupResponse lookup(Collection<UUID> variantIds) {
        return new InternalVariantLookupResponse(variants.findInternalLookupRows(variantIds).stream()
                .map(row -> new InternalVariantItem(
                        (UUID) row[0], (UUID) row[1], (String) row[2], (String) row[3], (String) row[4],
                        (String) row[5], (String) row[6], ((Number) row[7]).longValue(), (String) row[8],
                        (Boolean) row[9]))
                .toList());
    }
}
