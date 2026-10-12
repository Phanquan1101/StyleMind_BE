package com.stylemind.identity.dto.address;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProvinceListResponse(@JsonProperty("dataset_version") String datasetVersion,
        @JsonProperty("provinces") List<ProvinceResponse> provinces) {
}
