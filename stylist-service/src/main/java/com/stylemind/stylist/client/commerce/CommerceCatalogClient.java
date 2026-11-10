package com.stylemind.stylist.client.commerce;

import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.ProductListResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantBatchLookupResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantImageResponse;
import com.stylemind.stylist.client.commerce.CommerceCatalogContracts.VariantResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import feign.FeignException;
import feign.RetryableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

/** Validates the producer's actual DTOs and joins only by Commerce IDs. */
@Component
public class CommerceCatalogClient {
    private static final Duration DEFAULT_HYDRATION_TIMEOUT = Duration.ofSeconds(10);
    private final CommerceCatalogFeignClient feign;
    private final ThreadPoolExecutor productExecutor = createProductExecutor();

    public CommerceCatalogClient(CommerceCatalogFeignClient feign) {
        this.feign = feign;
    }

    public ProductListResponse listProducts(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw protocolError();
        try {
            ProductListResponse response = feign.listProducts(page, size, "WOMEN");
            if (response == null || response.items() == null || response.page() != page
                    || response.size() != size || response.totalElements() < 0 || response.totalPages() < 0
                    || response.items().size() > size) throw protocolError();
            return response;
        } catch (RuntimeException exception) {
            throw dependencyFailure(exception);
        }
    }

    public Map<UUID, HydratedVariant> hydrate(Map<UUID, UUID> expectedProductByVariant) {
        return hydrate(expectedProductByVariant, DEFAULT_HYDRATION_TIMEOUT);
    }

    public Map<UUID, HydratedVariant> hydrate(Map<UUID, UUID> expectedProductByVariant, Duration timeout) {
        if (expectedProductByVariant == null || expectedProductByVariant.isEmpty()) return Map.of();
        if (expectedProductByVariant.size() > 40
                || expectedProductByVariant.keySet().stream().anyMatch(id -> id == null)
                || expectedProductByVariant.values().stream().anyMatch(id -> id == null)
                || timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw protocolError();
        }
        long deadline = System.nanoTime() + timeout.toNanos();

        VariantBatchLookupResponse response;
        try {
            response = feign.lookupActiveVariants(new CommerceCatalogContracts.VariantBatchLookupRequest(
                    List.copyOf(expectedProductByVariant.keySet())));
        } catch (RuntimeException exception) {
            throw dependencyFailure(exception);
        }
        return hydrateResponse(expectedProductByVariant, response, deadline);
    }

    public Map<UUID, HydratedVariant> hydrateCurrentVariants(List<UUID> variantIds) {
        return hydrateCurrentVariants(variantIds, DEFAULT_HYDRATION_TIMEOUT);
    }

    public Map<UUID, HydratedVariant> hydrateCurrentVariants(List<UUID> variantIds, Duration timeout) {
        if (variantIds == null || variantIds.isEmpty()) return Map.of();
        if (variantIds.size() > 40 || variantIds.stream().anyMatch(Objects::isNull)
                || timeout == null || timeout.isNegative() || timeout.isZero()
                || variantIds.stream().distinct().count() != variantIds.size()) throw protocolError();
        long deadline = System.nanoTime() + timeout.toNanos();
        VariantBatchLookupResponse response;
        try {
            response = feign.lookupActiveVariants(new CommerceCatalogContracts.VariantBatchLookupRequest(variantIds));
        } catch (RuntimeException exception) {
            throw dependencyFailure(exception);
        }
        if (response == null || response.items() == null) throw protocolError();
        Set<UUID> requested = Set.copyOf(variantIds);
        Map<UUID, UUID> expected = new LinkedHashMap<>();
        Set<UUID> returned = new HashSet<>();
        for (VariantResponse variant : response.items()) {
            validateVariantShape(variant);
            if (!requested.contains(variant.variantId()) || !returned.add(variant.variantId())
                    || !Set.of("ACTIVE", "INACTIVE").contains(variant.variantStatus())) throw protocolError();
            validatePrice(variant.unitPriceVnd());
            expected.put(variant.variantId(), variant.productId());
        }
        return hydrateResponse(expected, response, deadline);
    }

    private Map<UUID, HydratedVariant> hydrateResponse(Map<UUID, UUID> expectedProductByVariant,
                                                       VariantBatchLookupResponse response, long deadline) {
        if (response == null || response.items() == null) throw protocolError();

        Map<UUID, VariantResponse> activeVariants = new LinkedHashMap<>();
        for (VariantResponse variant : response.items()) {
            validateVariantShape(variant);
            UUID expectedProductId = expectedProductByVariant.get(variant.variantId());
            if (expectedProductId == null || !expectedProductId.equals(variant.productId())
                    || activeVariants.containsKey(variant.variantId())) {
                throw protocolError();
            }
            validatePrice(variant.unitPriceVnd());
            if (!Set.of("ACTIVE", "INACTIVE").contains(variant.variantStatus())) throw protocolError();
            if ("ACTIVE".equals(variant.variantStatus())) activeVariants.put(variant.variantId(), variant);
        }
        if (activeVariants.isEmpty()) return Map.of();

        Set<UUID> productIds = new HashSet<>();
        activeVariants.values().forEach(variant -> productIds.add(variant.productId()));
        Map<UUID, Optional<ProductResponse>> products = lookupCurrentProducts(productIds, deadline);

        Map<UUID, HydratedVariant> hydrated = new LinkedHashMap<>();
        for (Map.Entry<UUID, VariantResponse> entry : activeVariants.entrySet()) {
            VariantResponse batchVariant = entry.getValue();
            Optional<ProductResponse> maybeProduct = products.get(batchVariant.productId());
            if (maybeProduct == null || maybeProduct.isEmpty()) continue;
            ProductResponse product = maybeProduct.orElseThrow();
            validateProductShape(product, batchVariant.productId());
            Map<UUID, VariantResponse> detailVariants = validateDetailVariants(product);
            VariantResponse detailVariant = detailVariants.get(entry.getKey());
            // Commerce may change the variant between its batch and detail reads; absence means ineligible now.
            if (detailVariant == null) continue;
            if (!sameVariant(batchVariant, detailVariant)) throw protocolError();

            long price = exactPrice(batchVariant.unitPriceVnd());
            List<CatalogImage> images = batchVariant.images().stream().map(CommerceCatalogClient::image).toList();
            if (!"ACTIVE".equals(product.productStatus())) continue;
            hydrated.put(entry.getKey(), new HydratedVariant(
                    product.productId(), batchVariant.variantId(), product.categoryId(), product.brandId(),
                    product.name(), product.categoryName(), product.brandName(), product.demographicSegment(),
                    product.productStatus(), batchVariant.variantStatus(), batchVariant.color(), batchVariant.size(),
                    batchVariant.material(), price, images));
        }
        return Map.copyOf(hydrated);
    }

    private Optional<ProductResponse> getCurrentProduct(UUID productId) {
        try {
            ProductResponse product = feign.getProduct(productId);
            if (product == null) throw protocolError();
            return Optional.of(product);
        } catch (FeignException.NotFound notFound) {
            return Optional.empty();
        } catch (RuntimeException exception) {
            throw dependencyFailure(exception);
        }
    }

    private Map<UUID, Optional<ProductResponse>> lookupCurrentProducts(Set<UUID> productIds, long deadline) {
        Map<UUID, Future<Optional<ProductResponse>>> pending = new LinkedHashMap<>();
        try {
            for (UUID productId : productIds.stream().sorted().toList()) {
                pending.put(productId, productExecutor.submit(() -> getCurrentProduct(productId)));
            }
        } catch (RejectedExecutionException exception) {
            pending.values().forEach(future -> future.cancel(true));
            throw new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        Map<UUID, Optional<ProductResponse>> products = new LinkedHashMap<>();
        try {
            for (Map.Entry<UUID, Future<Optional<ProductResponse>>> entry : pending.entrySet()) {
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) throw new TimeoutException();
                products.put(entry.getKey(), entry.getValue().get(remaining, TimeUnit.NANOSECONDS));
            }
            return products;
        } catch (TimeoutException exception) {
            pending.values().forEach(future -> future.cancel(true));
            throw new StylistApiException(ErrorCode.GENERATION_PROVIDER_TIMEOUT);
        } catch (InterruptedException exception) {
            pending.values().forEach(future -> future.cancel(true));
            Thread.currentThread().interrupt();
            throw new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        } catch (ExecutionException exception) {
            pending.values().forEach(future -> future.cancel(true));
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw protocolError();
        }
    }

    private static ThreadPoolExecutor createProductExecutor() {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, "stylist-commerce-product-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return new ThreadPoolExecutor(8, 8, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(256), factory, new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    void stopProductExecutor() {
        productExecutor.shutdownNow();
    }

    private static void validateProductShape(ProductResponse product, UUID requestedProductId) {
        if (product.productId() == null || !requestedProductId.equals(product.productId())
                || product.categoryId() == null || product.brandId() == null
                || blank(product.categoryName()) || blank(product.brandName()) || blank(product.name())
                || blank(product.demographicSegment()) || blank(product.productStatus())
                || product.variants() == null
                || !Set.of("ACTIVE", "INACTIVE").contains(product.productStatus())) {
            throw protocolError();
        }
    }

    private static Map<UUID, VariantResponse> validateDetailVariants(ProductResponse product) {
        Map<UUID, VariantResponse> variants = new HashMap<>();
        for (VariantResponse variant : product.variants()) {
            validateVariantShape(variant);
            if (!product.productId().equals(variant.productId()) || variants.putIfAbsent(variant.variantId(), variant) != null) {
                throw protocolError();
            }
            validatePrice(variant.unitPriceVnd());
            if (!Set.of("ACTIVE", "INACTIVE").contains(variant.variantStatus())) throw protocolError();
        }
        return variants;
    }

    private static void validateVariantShape(VariantResponse variant) {
        if (variant == null || variant.variantId() == null || variant.productId() == null
                || blank(variant.sku()) || blank(variant.variantStatus()) || variant.images() == null) {
            throw protocolError();
        }
        Set<UUID> imageIds = new HashSet<>();
        for (VariantImageResponse image : variant.images()) {
            if (image == null || image.imageId() == null || !variant.variantId().equals(image.variantId())
                    || blank(image.imageUrl()) || image.displayOrder() == null || image.displayOrder() < 0
                    || image.primary() == null || !imageIds.add(image.imageId())) {
                throw protocolError();
            }
        }
    }

    private static boolean sameVariant(VariantResponse left, VariantResponse right) {
        return left.variantId().equals(right.variantId()) && left.productId().equals(right.productId())
                && left.sku().equals(right.sku()) && equal(left.size(), right.size())
                && equal(left.color(), right.color()) && equal(left.material(), right.material())
                && exactPrice(left.unitPriceVnd()) == exactPrice(right.unitPriceVnd())
                && left.variantStatus().equals(right.variantStatus()) && left.images().equals(right.images());
    }

    private static CatalogImage image(VariantImageResponse image) {
        return new CatalogImage(image.imageId(), image.imageUrl(), image.altText(), image.displayOrder(), image.primary());
    }

    private static long exactPrice(BigDecimal price) {
        if (price == null) throw protocolError();
        try {
            long value = price.longValueExact();
            if (value < 0) throw protocolError();
            return value;
        } catch (ArithmeticException exception) {
            throw protocolError();
        }
    }

    private static void validatePrice(BigDecimal price) {
        exactPrice(price);
    }

    private static StylistApiException dependencyFailure(RuntimeException exception) {
        if (exception instanceof StylistApiException apiException) return apiException;
        if (exception instanceof RetryableException) {
            return new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
        }
        if (exception instanceof FeignException feignException) {
            int status = feignException.status();
            if (status == 408 || status == 429 || status >= 500) {
                return new StylistApiException(ErrorCode.DEPENDENCY_UNAVAILABLE);
            }
        }
        return protocolError();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean equal(Object left, Object right) {
        return Objects.equals(left, right);
    }

    private static StylistApiException protocolError() {
        return new StylistApiException(ErrorCode.DEPENDENCY_PROTOCOL_ERROR);
    }

    public record HydratedVariant(
            UUID productId,
            UUID variantId,
            UUID categoryId,
            UUID brandId,
            String productName,
            String categoryName,
            String brandName,
            String demographicSegment,
            String productStatus,
            String variantStatus,
            String color,
            String size,
            String material,
            long unitPriceVnd,
            List<CatalogImage> images) {
        public HydratedVariant {
            images = List.copyOf(images);
        }
    }

    public record CatalogImage(UUID imageId, String imageUrl, String altText, int displayOrder, boolean primary) { }
}
