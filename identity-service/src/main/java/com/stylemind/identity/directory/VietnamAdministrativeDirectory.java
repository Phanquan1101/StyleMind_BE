package com.stylemind.identity.directory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylemind.identity.dto.ProvinceListResponse;
import com.stylemind.identity.dto.ProvinceResponse;
import com.stylemind.identity.dto.WardListResponse;
import com.stylemind.identity.dto.WardResponse;
import com.stylemind.identity.errors.ApiOperationException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public final class VietnamAdministrativeDirectory {
    public static final String RESOURCE = "data/vietnam-administrative/locations-v5.2.0.json";
    private final String datasetVersion;
    private final List<ProvinceResponse> provinces;
    private final Map<String, List<WardResponse>> wardsByProvince;
    private final Map<String, Boolean> wardCodes;

    public VietnamAdministrativeDirectory(ObjectMapper mapper) {
        try (InputStream stream = new ClassPathResource(RESOURCE).getInputStream()) {
            Parsed parsed = parse(mapper.readTree(stream));
            if (!"v5.2.0".equals(parsed.datasetVersion())
                    || !"388/NQ-UBTVQH16".equals(parsed.latestDecree())
                    || !"2026-09-20T13:13:17Z".equals(parsed.generatedAt())
                    || parsed.provinces().size() != 34 || parsed.wardCodes().size() != 3321) {
                throw new IllegalArgumentException("Pinned directory metadata or record count is unexpected");
            }
            this.datasetVersion = parsed.datasetVersion();
            this.provinces = parsed.provinces();
            this.wardsByProvince = parsed.wardsByProvince();
            this.wardCodes = parsed.wardCodes();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Pinned Vietnam administrative directory is invalid", exception);
        }
    }

    public ProvinceListResponse provinces() {
        return new ProvinceListResponse(datasetVersion, provinces);
    }

    public WardListResponse wards(String provinceCode) {
        List<WardResponse> values = wardsByProvince.get(provinceCode);
        if (values == null) {
            throw new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found", 0);
        }
        return new WardListResponse(datasetVersion, provinceCode, values);
    }

    public boolean containsWard(String code) {
        return wardCodes.containsKey(code);
    }

    static Parsed parse(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("metadata").isObject()
                || !root.path("provinces").isArray() || !root.path("wards").isArray()) {
            throw new IllegalArgumentException("Directory root shape is invalid");
        }
        String version = requiredText(root.path("metadata").path("dataset_version"));
        String decree = requiredText(root.path("metadata").path("latest_decree"));
        String generatedAt = requiredText(root.path("metadata").path("generated_at"));

        Map<String, ProvinceResponse> provinceMap = new LinkedHashMap<>();
        for (JsonNode province : root.path("provinces")) {
            String code = requiredText(province.path("code"));
            String name = requiredText(province.path("name"));
            if (provinceMap.putIfAbsent(code, new ProvinceResponse(code, name)) != null) {
                throw new IllegalArgumentException("Duplicate province code");
            }
        }
        if (provinceMap.isEmpty()) {
            throw new IllegalArgumentException("No provinces supplied");
        }

        Map<String, List<WardResponse>> byProvince = new LinkedHashMap<>();
        provinceMap.keySet().forEach(code -> byProvince.put(code, new ArrayList<>()));
        Map<String, Boolean> codes = new LinkedHashMap<>();
        for (JsonNode ward : root.path("wards")) {
            String code = requiredText(ward.path("code"));
            String name = requiredText(ward.path("name"));
            String provinceCode = requiredText(ward.path("province_code"));
            List<WardResponse> target = byProvince.get(provinceCode);
            if (target == null) {
                throw new IllegalArgumentException("Ward references an unknown province");
            }
            if (codes.putIfAbsent(code, Boolean.TRUE) != null) {
                throw new IllegalArgumentException("Duplicate ward code");
            }
            target.add(new WardResponse(code, name));
        }
        byProvince.replaceAll((ignored, values) -> values.stream()
                .sorted(java.util.Comparator.comparing(WardResponse::code)).toList());
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("No wards supplied");
        }
        return new Parsed(version, decree, generatedAt, provinceMap.values().stream()
                .sorted(java.util.Comparator.comparing(ProvinceResponse::code)).toList(),
                Collections.unmodifiableMap(byProvince), Collections.unmodifiableMap(codes));
    }

    private static String requiredText(JsonNode value) {
        if (value == null || !value.isTextual()
                || value.asText().codePoints().noneMatch(cp -> !Character.isWhitespace(cp) && !Character.isSpaceChar(cp))) {
            throw new IllegalArgumentException("Required directory string is blank or not textual");
        }
        return value.asText();
    }

    record Parsed(String datasetVersion, String latestDecree, String generatedAt, List<ProvinceResponse> provinces,
            Map<String, List<WardResponse>> wardsByProvince, Map<String, Boolean> wardCodes) {
    }
}
