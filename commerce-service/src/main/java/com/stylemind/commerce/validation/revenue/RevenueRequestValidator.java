package com.stylemind.commerce.validation.revenue;

import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.errors.FieldError;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Query contract: optional from/to (yyyy-MM-dd, inclusive, shop-local days; default last 30 days) and group=day|week|month. */
@Component
public class RevenueRequestValidator {

    public static final int DEFAULT_DAYS = 30;
    public static final int MAX_DAYS = 366;
    private static final Set<String> FIELDS = Set.of("from", "to", "group");

    public enum Group {
        DAY, WEEK, MONTH;

        public String wire() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public RevenueQuery query(HttpServletRequest request, LocalDate today) {
        Map<String, String[]> params = request.getParameterMap();
        List<FieldError> errors = new ArrayList<>();
        for (String key : params.keySet()) {
            if (!FIELDS.contains(key)) {
                errors.add(new FieldError(key, "UNKNOWN_FIELD", "Tham số không được hỗ trợ."));
            }
        }
        LocalDate from = date(params, "from", errors);
        LocalDate to = date(params, "to", errors);
        Group group = group(params, errors);
        if (errors.isEmpty() && (from == null) != (to == null)) {
            errors.add(new FieldError(from == null ? "from" : "to", "REQUIRED", "Cần cung cấp đủ cả from và to."));
        }
        if (errors.isEmpty() && from == null) {
            to = today;
            from = today.minusDays(DEFAULT_DAYS - 1L);
        }
        if (errors.isEmpty()) {
            if (from.isAfter(to)) {
                errors.add(new FieldError("from", "OUT_OF_RANGE", "Ngày bắt đầu phải trước hoặc bằng ngày kết thúc."));
            } else if (to.isAfter(today)) {
                errors.add(new FieldError("to", "OUT_OF_RANGE", "Ngày kết thúc không được ở tương lai."));
            } else if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
                errors.add(new FieldError("from", "OUT_OF_RANGE", "Khoảng thời gian tối đa " + MAX_DAYS + " ngày."));
            }
        }
        if (!errors.isEmpty()) {
            throw ApiOperationException.invalidFields(errors);
        }
        return new RevenueQuery(from, to, group);
    }

    private Group group(Map<String, String[]> params, List<FieldError> errors) {
        String[] values = params.get("group");
        if (values == null) {
            return Group.DAY;
        }
        if (values.length == 1 && values[0] != null) {
            for (Group candidate : Group.values()) {
                if (candidate.wire().equals(values[0].strip())) {
                    return candidate;
                }
            }
        }
        errors.add(new FieldError("group", "INVALID_VALUE", "group phải là day, week hoặc month."));
        return Group.DAY;
    }

    private LocalDate date(Map<String, String[]> params, String name, List<FieldError> errors) {
        String[] values = params.get(name);
        if (values == null) {
            return null;
        }
        if (values.length != 1 || values[0] == null || values[0].isBlank()) {
            errors.add(new FieldError(name, "INVALID_FORMAT", "Ngày phải có dạng yyyy-MM-dd."));
            return null;
        }
        try {
            return LocalDate.parse(values[0].strip());
        } catch (DateTimeParseException exception) {
            errors.add(new FieldError(name, "INVALID_FORMAT", "Ngày phải có dạng yyyy-MM-dd."));
            return null;
        }
    }

    /** Inclusive day range plus the grouping. */
    public record RevenueQuery(LocalDate from, LocalDate to, Group group) {
        public int days() {
            return (int) (ChronoUnit.DAYS.between(from, to) + 1);
        }
    }
}
