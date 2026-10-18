package com.stylemind.commerce.repository.branch;

import com.stylemind.commerce.entity.Branch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {
    List<Branch> findAllByOrderByNameAscBranchIdAsc();
    List<Branch> findAllByBranchStatusOrderByNameAscBranchIdAsc(String branchStatus);
    java.util.Optional<Branch> findByBranchIdAndBranchStatus(UUID branchId, String branchStatus);
}
