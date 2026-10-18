package com.stylemind.commerce.service.branch;

import com.stylemind.commerce.dto.branch.BranchListResponse;
import com.stylemind.commerce.dto.branch.BranchResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.model.branch.Branch;
import com.stylemind.commerce.model.branch.BranchCommand;
import com.stylemind.commerce.repository.branch.BranchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository repository;

    @Transactional(readOnly = true)
    public BranchListResponse listPublic() {
        return new BranchListResponse(repository.list(true).stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getPublic(UUID branchId) {
        return response(repository.find(branchId, true).orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public BranchListResponse listAdmin() {
        return new BranchListResponse(repository.list(false).stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getAdmin(UUID branchId) {
        return response(repository.find(branchId, false).orElseThrow(this::notFound));
    }

    @Transactional
    public BranchResponse create(BranchCommand command) {
        try {
            return response(repository.insert(UUID.randomUUID(), command));
        } catch (DuplicateKeyException exception) {
            throw duplicateCode();
        }
    }

    @Transactional
    public BranchResponse replace(UUID branchId, BranchCommand command) {
        try {
            return response(repository.update(branchId, command).orElseThrow(this::notFound));
        } catch (DuplicateKeyException exception) {
            throw duplicateCode();
        }
    }

    @Transactional
    public void delete(UUID branchId) {
        try {
            if (repository.delete(branchId) != 1) {
                throw notFound();
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "BRANCH_IN_USE",
                    "Branch is used by existing data");
        }
    }

    private BranchResponse response(Branch branch) {
        return new BranchResponse(branch.branchId(), branch.branchCode(), branch.name(), branch.addressLine(),
                branch.contactPhone(), branch.branchStatus().name());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException duplicateCode() {
        return new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_BRANCH_CODE",
                "A branch with this code already exists");
    }
}
