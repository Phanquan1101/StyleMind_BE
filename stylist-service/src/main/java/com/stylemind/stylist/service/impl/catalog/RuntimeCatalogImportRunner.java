package com.stylemind.stylist.service.impl.catalog;

import com.stylemind.stylist.client.commerce.CommerceCatalogClient;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductListResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore;
import com.stylemind.stylist.repository.retrieval.RuntimeQdrantVectorStore.IndexPoint;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy;
import com.stylemind.stylist.service.catalog.ReviewedCatalogPolicy.Policy;
import com.stylemind.stylist.service.retrieval.RuntimeEmbeddingPort;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Explicit live-catalog rebuild. No import runs during normal application startup. */
@Component
@Profile("catalog-import")
@ConditionalOnProperty(name = "stylemind.stylist.catalog.import-enabled", havingValue = "true")
public class RuntimeCatalogImportRunner implements ApplicationRunner {
    public static final int PAGE_SIZE = 100;
    public static final int MAX_PAGES = 100;
    public static final int MAX_VARIANTS = 20_000;
    private static final int MAX_ALLOWLISTED_VARIANTS = 10;
    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?\\d[\\d .()-]{7,}\\d)(?!\\d)");

    private final CommerceCatalogClient commerce;
    private final ReviewedCatalogPolicy policyLoader;
    private final RuntimeEmbeddingPort embeddings;
    private final RuntimeQdrantVectorStore qdrant;
    private final Set<UUID> variantAllowlist;

    @Autowired
    public RuntimeCatalogImportRunner(CommerceCatalogClient commerce, ReviewedCatalogPolicy policyLoader,
            RuntimeEmbeddingPort embeddings, RuntimeQdrantVectorStore qdrant,
            @Value("${stylemind.stylist.catalog.import-variant-ids:}") String configuredVariantIds) {
        this(commerce, policyLoader, embeddings, qdrant, parseVariantAllowlist(configuredVariantIds));
    }

    /** Convenience constructor for existing tests and unrestricted legacy imports. */
    public RuntimeCatalogImportRunner(CommerceCatalogClient commerce, ReviewedCatalogPolicy policyLoader,
            RuntimeEmbeddingPort embeddings, RuntimeQdrantVectorStore qdrant) {
        this(commerce, policyLoader, embeddings, qdrant, Set.of());
    }

    private RuntimeCatalogImportRunner(CommerceCatalogClient commerce, ReviewedCatalogPolicy policyLoader,
            RuntimeEmbeddingPort embeddings, RuntimeQdrantVectorStore qdrant, Set<UUID> variantAllowlist) {
        this.commerce = commerce;
        this.policyLoader = policyLoader;
        this.embeddings = embeddings;
        this.qdrant = qdrant;
        this.variantAllowlist = Set.copyOf(variantAllowlist);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!embeddingsConfigured()) throw new StylistApiException(ErrorCode.GENERATION_RUNTIME_UNCONFIGURED);
        Policy policy = policyLoader.require();
        List<Indexable> catalog = readCatalog(policy);
        if (!variantAllowlist.isEmpty()
                && catalog.stream().map(Indexable::productId).distinct().count() != catalog.size()) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        if (catalog.isEmpty() || catalog.stream().noneMatch(item -> "TOP".equals(item.slot()))
                || catalog.stream().noneMatch(item -> "BOTTOM".equals(item.slot()))) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        List<IndexPoint> points = new ArrayList<>(catalog.size());
        for (int offset = 0; offset < catalog.size(); offset += 64) {
            List<Indexable> batch = catalog.subList(offset, Math.min(offset + 64, catalog.size()));
            List<float[]> vectors = embeddings.embedDocuments(batch.stream().map(Indexable::text).toList());
            if (vectors.size() != batch.size()) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            for (int i = 0; i < batch.size(); i++) points.add(point(batch.get(i), vectors.get(i), policy));
        }
        String collection = "stylemind_stylist_catalog_" + UUID.randomUUID().toString().replace("-", "");
        qdrant.buildAndPublish(collection, policy, points);
    }

    private boolean embeddingsConfigured() { return embeddings.isConfigured() && qdrant.isConfigured()
            && embeddings.metadata().equals(qdrant.embeddingMetadata()); }

    private List<Indexable> readCatalog(Policy policy) {
        List<Indexable> candidates = new ArrayList<>();
        Set<UUID> productIds = new HashSet<>();
        Set<UUID> variantIds = new HashSet<>();
        Set<UUID> matchedAllowlistIds = new HashSet<>();
        long advertisedTotalPages = -1;
        long advertisedTotalElements = -1;
        long receivedProducts = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            ProductListResponse response = commerce.listProducts(page, PAGE_SIZE);
            if (page == 0) {
                advertisedTotalPages = response.totalPages();
                advertisedTotalElements = response.totalElements();
            }
            if (response.totalPages() != advertisedTotalPages || response.totalElements() != advertisedTotalElements
                    || advertisedTotalPages > MAX_PAGES || advertisedTotalElements > (long) MAX_PAGES * PAGE_SIZE
                    || (advertisedTotalElements == 0) != (advertisedTotalPages == 0)) {
                throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            }
            if (advertisedTotalPages == 0) {
                if (receivedProducts != advertisedTotalElements || !response.items().isEmpty()) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                }
                requireAllAllowlistIdsMatched(matchedAllowlistIds);
                return List.of();
            }
            receivedProducts = Math.addExact(receivedProducts, response.items().size());
            for (ProductResponse product : response.items()) {
                validateProduct(product);
                if (!productIds.add(product.productId())) throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                for (VariantResponse variant : product.variants()) {
                    validateVariant(product, variant);
                    if (!variantIds.add(variant.variantId())) {
                        throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                    }
                    if (variantIds.size() > MAX_VARIANTS) throw new StylistApiException(ErrorCode.PAYLOAD_TOO_LARGE);
                }
                if (!"ACTIVE".equals(product.productStatus()) || !"WOMEN".equals(product.demographicSegment())) continue;
                var mapping = policy.slot(product.categoryId(), product.demographicSegment());
                if (mapping.isEmpty()) continue;
                for (VariantResponse variant : product.variants()) {
                    if (!"ACTIVE".equals(variant.variantStatus())) continue;
                    if (!variantAllowlist.isEmpty() && !variantAllowlist.contains(variant.variantId())) continue;
                    String text = indexText(product.name(), variant.color(), variant.size(), variant.material());
                    if (text.isBlank()) continue;
                    matchedAllowlistIds.add(variant.variantId());
                    candidates.add(new Indexable(product.productId(), variant.variantId(), product.categoryId(),
                            product.categoryName(), mapping.orElseThrow(), product.demographicSegment(),
                            product.name(), null, variant.color(), variant.size(), variant.material(), text));
                }
            }
            if (page + 1 >= advertisedTotalPages) {
                if (receivedProducts != advertisedTotalElements || page + 1 != advertisedTotalPages) {
                    throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
                }
                requireAllAllowlistIdsMatched(matchedAllowlistIds);
                return List.copyOf(candidates);
            }
        }
        throw new StylistApiException(ErrorCode.PAYLOAD_TOO_LARGE);
    }

    private void requireAllAllowlistIdsMatched(Set<UUID> matchedIds) {
        if (!matchedIds.containsAll(variantAllowlist)) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
    }

    private static Set<UUID> parseVariantAllowlist(String configuredIds) {
        if (configuredIds == null || configuredIds.isBlank()) return Set.of();
        String[] rawIds = configuredIds.split(",", -1);
        if (rawIds.length > MAX_ALLOWLISTED_VARIANTS) {
            throw new IllegalArgumentException("Catalog import variant allowlist supports at most 10 IDs");
        }
        Set<UUID> ids = new HashSet<>();
        for (String rawId : rawIds) {
            String value = rawId.trim();
            try {
                UUID id = UUID.fromString(value);
                if (!id.toString().equalsIgnoreCase(value) || !ids.add(id)) {
                    throw new IllegalArgumentException("Catalog import variant IDs must be unique canonical UUIDs");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Catalog import variant IDs must be unique canonical UUIDs", exception);
            }
        }
        return Set.copyOf(ids);
    }

    private static void validateProduct(ProductResponse product) {
        if (product == null || product.productId() == null || product.categoryId() == null || product.brandId() == null
                || invalidRequiredText(product.name(), 160) || invalidRequiredText(product.categoryName(), 160)
                || invalidRequiredText(product.brandName(), 160)
                || invalidOptionalText(product.description(), 2_000)
                || product.demographicSegment() == null
                || !Set.of("WOMEN", "MEN", "UNISEX").contains(product.demographicSegment())
                || product.productStatus() == null
                || !Set.of("ACTIVE", "INACTIVE").contains(product.productStatus()) || product.variants() == null) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
    }

    private static void validateVariant(ProductResponse product, VariantResponse variant) {
        if (variant == null || variant.variantId() == null || !product.productId().equals(variant.productId())
                || invalidRequiredText(variant.sku(), 120) || invalidOptionalText(variant.size(), 80)
                || invalidOptionalText(variant.color(), 80) || invalidOptionalText(variant.material(), 120)
                || variant.variantStatus() == null
                || !Set.of("ACTIVE", "INACTIVE").contains(variant.variantStatus())
                || variant.unitPriceVnd() == null || exactNonNegativePrice(variant.unitPriceVnd()) < 0
                || variant.images() == null) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        Set<UUID> imageIds = new HashSet<>();
        for (var image : variant.images()) {
            if (image == null || image.imageId() == null || !variant.variantId().equals(image.variantId())
                    || image.imageUrl() == null || image.imageUrl().isBlank() || image.displayOrder() == null
                    || image.displayOrder() < 0 || image.primary() == null || !imageIds.add(image.imageId())) {
                throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
            }
        }
    }

    private static boolean invalidRequiredText(String value, int maxCodePoints) {
        return value == null || value.isBlank() || invalidText(value, maxCodePoints);
    }

    /** Commerce nullableBoundedText allows null, but rejects blank strings and invalid values. */
    private static boolean invalidOptionalText(String value, int maxCodePoints) {
        return value != null && (value.isBlank() || invalidText(value, maxCodePoints));
    }

    private static boolean invalidText(String value, int maxCodePoints) {
        return value.codePointCount(0, value.length()) > maxCodePoints || value.indexOf('\0') >= 0;
    }

    private static long exactNonNegativePrice(BigDecimal price) {
        try {
            long value = price.longValueExact();
            return value < 0 ? -1 : value;
        } catch (ArithmeticException exception) {
            return -1;
        }
    }

    private static String indexText(String name, String color, String size, String material) {
        List<String> fields = new ArrayList<>();
        for (String field : List.of(name, color == null ? "" : color, size == null ? "" : size,
                material == null ? "" : material)) {
            if (field.isBlank()) continue;
            if (URL.matcher(field).find() || EMAIL.matcher(field).find() || PHONE.matcher(field).find()
                    || field.indexOf('\0') >= 0 || field.getBytes(StandardCharsets.UTF_8).length > 512) {
                continue;
            }
            fields.add(field.trim());
        }
        return String.join(" ", fields);
    }

    private IndexPoint point(Indexable candidate, float[] vector, Policy policy) {
        if (vector == null || vector.length != RuntimeQdrantVectorStore.DIMENSIONS
                || java.util.Arrays.stream(toDouble(vector)).anyMatch(value -> !Double.isFinite(value))) {
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_PROTOCOL_ERROR);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("record_type", "CATALOG_CANDIDATE");
        payload.put("catalog_source", "LIVE_COMMERCE");
        payload.put("product_id", candidate.productId().toString());
        payload.put("variant_id", candidate.variantId().toString());
        payload.put("category_id", candidate.categoryId().toString());
        payload.put("slot", candidate.slot());
        payload.put("demographic_segment", "WOMEN");
        payload.put("product_name", candidate.productName());
        payload.put("policy_revision", policy.revision());
        payload.put("text_template_revision", "catalog-v1");
        RuntimeEmbeddingPort.EmbeddingMetadata metadata = embeddings.metadata();
        payload.put("embedding_provider", metadata.provider());
        payload.put("embedding_model", metadata.model());
        payload.put("embedding_dimensions", metadata.dimensions());
        payload.put("embedding_configuration_version", metadata.configurationVersion());
        nullable(payload, "category_name", candidate.categoryName());
        nullable(payload, "color", candidate.color());
        nullable(payload, "size", candidate.size());
        nullable(payload, "material", candidate.material());
        return new IndexPoint(candidate.variantId(), vector, payload);
    }

    private static double[] toDouble(float[] input) {
        double[] output = new double[input.length];
        for (int i = 0; i < input.length; i++) output[i] = input[i];
        return output;
    }

    private static void nullable(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) map.put(key, value);
    }

    private record Indexable(UUID productId, UUID variantId, UUID categoryId, String categoryName,
                             String slot, String demographic, String productName, String description,
                             String color, String size, String material, String text) { }
}
