package com.stylemind.commerce.model.branch;

public record BranchCommand(
        String branchCode,
        String name,
        String addressLine,
        String contactPhone,
        BranchStatus branchStatus) {
}
