package com.stylemind.commerce.service.branch;

import com.stylemind.commerce.dto.branch.BranchListResponse;
import com.stylemind.commerce.dto.branch.BranchRequest;
import com.stylemind.commerce.dto.branch.BranchResponse;
import java.util.UUID;

public interface BranchService {

    BranchListResponse listPublic();

    BranchResponse getPublic(UUID branchId);

    BranchListResponse listAdmin();

    BranchResponse getAdmin(UUID branchId);

    BranchResponse create(BranchRequest command);

    BranchResponse replace(UUID branchId, BranchRequest command);

    void delete(UUID branchId);
}
