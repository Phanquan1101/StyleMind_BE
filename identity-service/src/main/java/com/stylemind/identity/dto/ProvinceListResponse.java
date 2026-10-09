package com.stylemind.identity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ProvinceListResponse(@JsonProperty("dataset_version") String datasetVersion,
        @JsonProperty("provinces") List<ProvinceResponse> provinces) {
}
