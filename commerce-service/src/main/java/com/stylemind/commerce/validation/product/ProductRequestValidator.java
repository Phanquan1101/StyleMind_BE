package com.stylemind.commerce.validation.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.product.ImageReorderRequest;
import com.stylemind.commerce.dto.product.VariantBatchLookupRequest;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.errors.FieldError;
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

    // Giới hạn kỹ thuật chống tràn số khi tính tổng (quantity × price), FR chưa quy định.
    private static final long MAX_UNIT_PRICE_VND = 1_000_000_000L;

    private static final Set<String> PRODUCT_FIELDS = Set.of(
            "category_id", "brand_id", "name", "description", "demographic_segment", "product_status");
    private static final Set<String> VARIANT_FIELDS = Set.of(
            "sku", "size", "color", "material", "unit_price_vnd", "quantity", "variant_status");
    private static final Set<String> LIST_QUERY_FIELDS = Set.of(
            "keyword", "category_id", "min_price_vnd", "max_price_vnd", "demographic_segment", "page", "size", "status");
    private static final Set<String> IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_IMAGE_BYTES = 10L * 1024L * 1024L;
    private static final String SKU_PATTERN = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";

    public ProductCommand productCommand(JsonNode body) {
        requireExactFields(body, PRODUCT_FIELDS);
        List<FieldError> errors = new ArrayList<>();
        UUID categoryId = requiredUuid(body, "category_id", "Vui lòng chọn danh mục", errors);
        UUID brandId = requiredUuid(body, "brand_id", "Vui lòng chọn thương hiệu", errors);
        String name = requiredText(body, "name", "Tên sản phẩm", 160, errors);
        String description = optionalText(body, "description", "Mô tả", 2000, true, errors);
        String demographic = optionalSyntaxCode(body, "demographic_segment", "Nhóm đối tượng", errors);
        ProductStatus status = requiredEnum(body, "product_status", "Trạng thái sản phẩm", ProductStatus.class, errors);
        throwIfAny(errors);
        return new ProductCommand(categoryId, brandId, name, description, demographic, status);
    }

    public VariantCommand variantCommand(JsonNode body) {
        requireExactFields(body, VARIANT_FIELDS);
        List<FieldError> errors = new ArrayList<>();
        String sku = requiredText(body, "sku", "Mã SKU", 64, errors);
        if (sku != null) {
            sku = sku.toUpperCase(Locale.ROOT);
            if (!sku.matches(SKU_PATTERN)) {
                errors.add(new FieldError("sku", "INVALID_FORMAT",
                        "Mã SKU chỉ gồm chữ, số, dấu gạch ngang và gạch dưới, bắt đầu bằng chữ hoặc số"));
            }
        }
        String size = optionalText(body, "size", "Kích thước", 64, errors);
        String color = optionalText(body, "color", "Màu sắc", 64, errors);
        String material = optionalText(body, "material", "Chất liệu", 64, errors);
        long price = integralNumber(body, "unit_price_vnd", "Giá", 0, MAX_UNIT_PRICE_VND,
                "Giá phải là số nguyên từ 0 đến 1.000.000.000 VND", errors);
        long quantity = integralNumber(body, "quantity", "Tồn kho", 0, Integer.MAX_VALUE,
                "Tồn kho phải là số nguyên từ 0 trở lên", errors);
        ProductVariantStatus status = requiredEnum(body, "variant_status", "Trạng thái biến thể",
                ProductVariantStatus.class, errors);
        throwIfAny(errors);
        return new VariantCommand(sku, size, color, material, price, (int) quantity, status);
    }

    public ProductListCriteria listCriteria(HttpServletRequest request) {
        return criteria(request, false);
    }

    /** Admin list: same filters plus status; keyword also matches SKU. */
    public ProductListCriteria adminListCriteria(HttpServletRequest request) {
        return criteria(request, true);
    }

    private ProductListCriteria criteria(HttpServletRequest request, boolean admin) {
        Map<String, String[]> parameters = request.getParameterMap();
        if (!LIST_QUERY_FIELDS.containsAll(parameters.keySet()) || (!admin && parameters.containsKey("status"))) {
            throw invalidRequest();
        }
        parameters.forEach((key, values) -> {
            // category_id may repeat (OR filter); everything else is single-valued
            if (values == null || values.length == 0 || values[0] == null
                    || (values.length != 1 && !"category_id".equals(key))) {
                throw invalidRequest();
            }
        });
        String keyword = queryText(parameters, "keyword", 120);
        String demographic = querySyntaxCode(parameters, "demographic_segment");
        String[] rawCategories = parameters.get("category_id");
        List<UUID> categoryIds = new ArrayList<>();
        if (rawCategories != null) {
            if (rawCategories.length > 20) {
                throw invalidRequest();
            }
            for (String raw : rawCategories) {
                UUID id = uuid(raw);
                if (!categoryIds.contains(id)) {
                    categoryIds.add(id);
                }
            }
        }
        String status = parameter(parameters, "status");
        if (status != null && !status.isBlank()) {
            try {
                status = ProductStatus.valueOf(status).name();
            } catch (IllegalArgumentException e) {
                throw invalidRequest();
            }
        } else {
            status = null;
        }
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
        return new ProductListCriteria(keyword, List.copyOf(categoryIds), minPrice, maxPrice, demographic, status, page, size);
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
        List<FieldError> errors = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            errors.add(new FieldError("file", "REQUIRED", "Vui lòng chọn ảnh sản phẩm"));
        } else if (file.getSize() > MAX_IMAGE_BYTES) {
            errors.add(new FieldError("file", "TOO_LARGE", "Ảnh tối đa " + (MAX_IMAGE_BYTES / (1024 * 1024)) + " MB"));
        } else if (file.getContentType() == null || !IMAGE_CONTENT_TYPES.contains(file.getContentType())) {
            errors.add(new FieldError("file", "INVALID_FORMAT", "Ảnh phải là JPG, PNG hoặc WebP"));
        }
        throwIfAny(errors);
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw ApiOperationException.invalidFields(errors);
        }
    }

    private static UUID requiredUuid(JsonNode body, String field, String message, List<FieldError> errors) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || (node.isTextual() && node.textValue().isBlank())) {
            errors.add(new FieldError(field, "REQUIRED", message));
            return null;
        }
        try {
            if (!node.isTextual()) {
                throw new IllegalArgumentException();
            }
            return UUID.fromString(node.textValue().strip());
        } catch (IllegalArgumentException exception) {
            errors.add(new FieldError(field, "INVALID_FORMAT", message));
            return null;
        }
    }

    /** Stripped text, or null (with an error recorded) when missing, blank, too long or containing control characters. */
    private static String requiredText(JsonNode body, String field, String label, int max, List<FieldError> errors) {
        int before = errors.size();
        String value = optionalText(body, field, label, max, errors);
        if (value == null && errors.size() == before) {
            errors.add(new FieldError(field, "REQUIRED", label + " không được để trống"));
        }
        return value;
    }

    private static String optionalText(JsonNode body, String field, String label, int max, List<FieldError> errors) {
        return optionalText(body, field, label, max, false, errors);
    }

    /** Descriptions are free text: line breaks and tabs are normal there (the imported catalog has them); other control characters are not. */
    private static String optionalText(JsonNode body, String field, String label, int max, boolean multiline,
            List<FieldError> errors) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            errors.add(new FieldError(field, "INVALID_TYPE", label + " phải là chuỗi ký tự"));
            return null;
        }
        String value = node.textValue().strip();
        if (value.isEmpty()) {
            return null;
        }
        if (value.codePointCount(0, value.length()) > max) {
            errors.add(new FieldError(field, "TOO_LONG", label + " tối đa " + max + " ký tự"));
            return null;
        }
        if (value.codePoints().anyMatch(cp -> Character.isISOControl(cp) && !(multiline && (cp == '\n' || cp == '\r' || cp == '\t')))) {
            errors.add(new FieldError(field, "INVALID_FORMAT", label + " chứa ký tự không hợp lệ"));
            return null;
        }
        return value;
    }

    private static String optionalSyntaxCode(JsonNode body, String field, String label, List<FieldError> errors) {
        String raw = optionalText(body, field, label, 64, errors);
        if (raw == null) {
            return null;
        }
        String value = raw.toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z0-9][A-Z0-9_-]{0,63}")) {
            errors.add(new FieldError(field, "INVALID_FORMAT", label + " chỉ gồm chữ, số, dấu gạch ngang và gạch dưới"));
            return null;
        }
        return value;
    }

    private static <E extends Enum<E>> E requiredEnum(JsonNode body, String field, String label, Class<E> type,
            List<FieldError> errors) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull() || (node.isTextual() && node.textValue().isBlank())) {
            errors.add(new FieldError(field, "REQUIRED", label + " không được để trống"));
            return null;
        }
        try {
            if (!node.isTextual()) {
                throw new IllegalArgumentException();
            }
            return Enum.valueOf(type, node.textValue());
        } catch (IllegalArgumentException exception) {
            errors.add(new FieldError(field, "INVALID_VALUE", label + " không hợp lệ"));
            return null;
        }
    }

    private static long integralNumber(JsonNode body, String field, String label, long min, long max,
            String rangeMessage, List<FieldError> errors) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            errors.add(new FieldError(field, "REQUIRED", label + " không được để trống"));
            return 0L;
        }
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            errors.add(new FieldError(field, "INVALID_TYPE", rangeMessage));
            return 0L;
        }
        long value = node.longValue();
        if (value < min || value > max) {
            errors.add(new FieldError(field, "OUT_OF_RANGE", rangeMessage));
            return 0L;
        }
        return value;
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
