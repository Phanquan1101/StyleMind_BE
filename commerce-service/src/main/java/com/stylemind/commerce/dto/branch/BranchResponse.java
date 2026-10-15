package com.stylemind.commerce.dto.branch;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record BranchResponse(
        @JsonProperty("branch_id") UUID branchId,
        @JsonProperty("branch_code") String branchCode,
        String name,
        @JsonProperty("address_line") String addressLine,
        @JsonProperty("contact_phone") String contactPhone,
        @JsonProperty("branch_status") String branchStatus) {
}
