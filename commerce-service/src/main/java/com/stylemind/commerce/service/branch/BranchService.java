package com.stylemind.commerce.service.branch;

import com.stylemind.commerce.dto.branch.BranchListResponse;
import com.stylemind.commerce.dto.branch.BranchRequest;
import com.stylemind.commerce.dto.branch.BranchResponse;
import com.stylemind.commerce.entity.Branch;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.branch.BranchRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository repository;

    @Transactional(readOnly = true)
    public BranchListResponse listPublic() {
        return new BranchListResponse(repository.findAllByBranchStatusOrderByNameAscBranchIdAsc("ACTIVE")
                .stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getPublic(UUID branchId) {
        return response(repository.findByBranchIdAndBranchStatus(branchId, "ACTIVE").orElseThrow(this::notFound));
    }

    @Transactional(readOnly = true)
    public BranchListResponse listAdmin() {
        return new BranchListResponse(repository.findAllByOrderByNameAscBranchIdAsc()
                .stream().map(this::response).toList());
    }

    @Transactional(readOnly = true)
    public BranchResponse getAdmin(UUID branchId) {
        return response(repository.findById(branchId).orElseThrow(this::notFound));
    }

    @Transactional
    public BranchResponse create(BranchRequest command) {
        try {
            Branch branch = new Branch();
            branch.setBranchId(UUID.randomUUID());
            apply(branch, command);
            return response(repository.saveAndFlush(branch));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    @Transactional
    public BranchResponse replace(UUID branchId, BranchRequest command) {
        try {
            Branch branch = repository.findById(branchId).orElseThrow(this::notFound);
            apply(branch, command);
            return response(repository.saveAndFlush(branch));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateCode();
        }
    }

    @Transactional
    public void delete(UUID branchId) {
        try {
            if (!repository.existsById(branchId)) {
                throw notFound();
            }
            repository.deleteById(branchId);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiOperationException(HttpStatus.CONFLICT, "BRANCH_IN_USE",
                    "Branch is used by existing data");
        }
    }

    private void apply(Branch branch, BranchRequest request) {
        branch.setBranchCode(request.branchCode());
        branch.setName(request.name());
        branch.setAddressLine(request.addressLine());
        branch.setContactPhone(request.contactPhone());
        branch.setBranchStatus(request.branchStatus().name());
    }

    private BranchResponse response(Branch branch) {
        return new BranchResponse(branch.getBranchId(), branch.getBranchCode(), branch.getName(), branch.getAddressLine(),
                branch.getContactPhone(), branch.getBranchStatus());
    }

    private ApiOperationException notFound() {
        return new ApiOperationException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Resource was not found");
    }

    private ApiOperationException duplicateCode() {
        return new ApiOperationException(HttpStatus.CONFLICT, "DUPLICATE_BRANCH_CODE",
                "A branch with this code already exists");
    }
}
