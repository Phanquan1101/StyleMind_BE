package com.stylemind.identity.dto.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record WardListResponse(@JsonProperty("dataset_version") String datasetVersion,
        @JsonProperty("province_code") String provinceCode, @JsonProperty("wards") List<WardResponse> wards) {
}
