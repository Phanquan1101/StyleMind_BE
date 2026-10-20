package com.stylemind.commerce.validation.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.product.ImageReorderRequest;
import com.stylemind.commerce.dto.product.VariantBatchLookupRequest;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.dto.product.ProductCommand;
import com.stylemind.commerce.dto.product.ProductListCriteria;
import com.stylemind.commerce.enums.ProductStatus;
import com.stylemind.commerce.enums.ProductVariantStatus;
import com.stylemind.commerce.dto.product.VariantCommand;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ProductRequestValidator {

    private static final Set<String> PRODUCT_FIELDS = Set.of(
            "category_id", "brand_id", "name", "description", "demographic_segment", "product_status");
    private static final Set<String> VARIANT_FIELDS = Set.of(
            "sku", "size", "color", "material", "unit_price_vnd", "variant_status");
    private static final Set<String> LIST_QUERY_FIELDS = Set.of(
            "keyword", "category_id", "min_price_vnd", "max_price_vnd", "demographic_segment", "page", "size");
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final String SKU_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";

    public ProductCommand productCommand(JsonNode body) {
        requireExactFields(body, PRODUCT_FIELDS);
        UUID categoryId = uuid(text(body, "category_id", false));
        UUID brandId = uuid(text(body, "brand_id", false));
        String name = boundedText(text(body, "name", false), 1, 160);
        String description = nullableBoundedText(body, "description", 2000);
        String demographic = nullableSyntaxCode(body, "demographic_segment");
        ProductStatus status = enumValue(text(body, "product_status", false), ProductStatus.class);
        return new ProductCommand(categoryId, brandId, name, description, demographic, status);
    }

    public VariantCommand variantCommand(JsonNode body) {
        requireExactFields(body, VARIANT_FIELDS);
        String sku = text(body, "sku", false).strip().toUpperCase(Locale.ROOT);
        if (!sku.matches(SKU_PATTERN)) {
            throw invalidRequest();
        }
        String size = nullableBoundedText(body, "size", 64);
        String color = nullableBoundedText(body, "color", 64);
        String material = nullableBoundedText(body, "material", 64);
        JsonNode price = body.get("unit_price_vnd");
        if (price == null || !price.canConvertToLong() || price.longValue() < 0) {
            throw invalidRequest();
        }
        ProductVariantStatus status = enumValue(text(body, "variant_status", false), ProductVariantStatus.class);
        return new VariantCommand(sku, size, color, material, price.longValue(), status);
    }

    public ProductListCriteria listCriteria(HttpServletRequest request) {
        Map<String, String[]> parameters = request.getParameterMap();
        if (!LIST_QUERY_FIELDS.containsAll(parameters.keySet())) {
            throw invalidRequest();
        }
        parameters.forEach((key, values) -> {
            if (values == null || values.length != 1 || values[0] == null) {
                throw invalidRequest();
            }
        });
        String keyword = queryText(parameters, "keyword", 120);
        String demographic = querySyntaxCode(parameters, "demographic_segment");
        UUID categoryId = parameter(parameters, "category_id") == null ? null : uuid(parameter(parameters, "category_id"));
        Long minPrice = parseLong(parameter(parameters, "min_price_vnd"));
        Long maxPrice = parseLong(parameter(parameters, "max_price_vnd"));
        if ((minPrice != null && minPrice < 0) || (maxPrice != null && maxPrice < 0)
                || (minPrice != null && maxPrice != null && minPrice > maxPrice)) {
            throw invalidRequest();
        }
        int page = parseInteger(parameter(parameters, "page"), 0);
        int size = parseInteger(parameter(parameters, "size"), 20);
        if (page < 0 || size < 1 || size > 100) {
            throw invalidRequest();
        }
        return new ProductListCriteria(keyword, categoryId, minPrice, maxPrice, demographic, page, size);
    }

    public List<UUID> variantIds(VariantBatchLookupRequest request) {
        if (request == null || request.variantIds() == null || request.variantIds().isEmpty()
                || request.variantIds().size() > 100) {
            throw invalidRequest();
        }
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        for (String raw : request.variantIds()) {
            if (!ids.add(uuid(raw))) {
                throw invalidRequest();
            }
        }
        return List.copyOf(ids);
    }

    public Map<UUID, Integer> imageOrder(ImageReorderRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw invalidRequest();
        }
        Map<UUID, Integer> order = new LinkedHashMap<>();
        Set<Integer> displayOrders = new LinkedHashSet<>();
        for (ImageReorderRequest.ImageOrderItem item : request.items()) {
            if (item == null || item.displayOrder() == null || item.displayOrder() < 0
                    || !displayOrders.add(item.displayOrder())) {
                throw invalidRequest();
            }
            UUID id = uuid(item.imageId());
            if (order.put(id, item.displayOrder()) != null) {
                throw invalidRequest();
            }
        }
        return order;
    }

    public UUID optionalPrimaryImageId(ImageReorderRequest request) {
        return request == null || request.primaryImageId() == null ? null : uuid(request.primaryImageId());
    }

    public UUID pathId(String raw) {
        return uuid(raw);
    }

    public String altText(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return boundedText(raw, 1, 160);
    }

    public void imageFile(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_IMAGE_BYTES
                || file.getContentType() == null || !IMAGE_CONTENT_TYPES.contains(file.getContentType())) {
            throw invalidRequest();
        }
    }

    private void requireExactFields(JsonNode body, Set<String> fields) {
        if (body == null || !body.isObject() || body.size() != fields.size()) {
            throw invalidRequest();
        }
        for (Iterator<String> names = body.fieldNames(); names.hasNext();) {
            if (!fields.contains(names.next())) {
                throw invalidRequest();
            }
        }
    }

    private String text(JsonNode body, String field, boolean nullable) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            if (nullable) {
                return null;
            }
            throw invalidRequest();
        }
        if (!node.isTextual()) {
            throw invalidRequest();
        }
        return node.textValue();
    }

    private String nullableBoundedText(JsonNode body, String field, int max) {
        String raw = text(body, field, true);
        return raw == null ? null : boundedText(raw, 1, max);
    }

    private String nullableSyntaxCode(JsonNode body, String field) {
        String raw = text(body, field, true);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.strip().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            throw invalidRequest();
        }
        return value;
    }

    private String boundedText(String raw, int min, int max) {
        String value = raw.strip();
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalidRequest();
        }
        return value;
    }

    private <E extends Enum<E>> E enumValue(String raw, Class<E> enumType) {
        try {
            return Enum.valueOf(enumType, raw);
        } catch (RuntimeException exception) {
            throw invalidRequest();
        }
    }

    private String queryText(Map<String, String[]> parameters, String name, int max) {
        String raw = parameter(parameters, name);
        if (raw == null) {
            return null;
        }
        return boundedText(raw, 1, max);
    }

    private String querySyntaxCode(Map<String, String[]> parameters, String name) {
        String raw = parameter(parameters, name);
        if (raw == null) {
            return null;
        }
        String value = raw.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty() || !value.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            throw invalidRequest();
        }
        return value;
    }

    private String parameter(Map<String, String[]> parameters, String name) {
        String[] values = parameters.get(name);
        return values == null ? null : values[0];
    }

    private Long parseLong(String raw) {
        if (raw == null) {
            return null;
        }
        if (raw.isEmpty() || !raw.chars().allMatch(Character::isDigit)) {
            throw invalidRequest();
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException exception) {
            throw invalidRequest();
        }
    }

    private int parseInteger(String raw, int defaultValue) {
        if (raw == null) {
            return defaultValue;
        }
        Long value = parseLong(raw);
        if (value > Integer.MAX_VALUE) {
            throw invalidRequest();
        }
        return value.intValue();
    }

    private UUID uuid(String raw) {
        if (raw == null) {
            throw invalidRequest();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw invalidRequest();
        }
    }

    private ApiOperationException invalidRequest() {
        return new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                "Request is missing or malformed");
    }
}
