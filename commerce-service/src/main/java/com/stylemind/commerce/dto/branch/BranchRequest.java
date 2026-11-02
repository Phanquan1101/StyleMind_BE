package com.stylemind.commerce.dto.branch;

import com.stylemind.commerce.enums.BranchStatus;

public record BranchRequest(
        String branchCode,
        String name,
        String addressLine,
        String contactPhone,
        BranchStatus branchStatus) {
}
