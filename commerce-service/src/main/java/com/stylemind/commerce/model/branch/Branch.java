package com.stylemind.commerce.model.branch;

import java.util.UUID;

public record Branch(
        UUID branchId,
        String branchCode,
        String name,
        String addressLine,
        String contactPhone,
        BranchStatus branchStatus) {
}
