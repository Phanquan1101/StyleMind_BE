package com.stylemind.commerce.controller.branch;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.branch.BranchListResponse;
import com.stylemind.commerce.dto.branch.BranchResponse;
import com.stylemind.commerce.service.branch.BranchService;
import com.stylemind.commerce.validation.branch.BranchRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService service;
    private final BranchRequestValidator validator;

    @GetMapping(path = "/api/v1/branches", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List active branches")
    public BranchListResponse listPublic() {
        return service.listPublic();
    }

    @GetMapping(path = "/api/v1/branches/{branchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get active branch")
    public BranchResponse getPublic(@PathVariable String branchId) {
        return service.getPublic(validator.pathId(branchId));
    }

    @GetMapping(path = "/api/v1/admin/branches", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List branches including inactive branches (ADMIN only)")
    public BranchListResponse listAdmin() {
        return service.listAdmin();
    }

    @GetMapping(path = "/api/v1/admin/branches/{branchId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get branch including inactive branches (ADMIN only)")
    public BranchResponse getAdmin(@PathVariable String branchId) {
        return service.getAdmin(validator.pathId(branchId));
    }

    @PostMapping(path = "/api/v1/admin/branches", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create branch (ADMIN only)")
    public ResponseEntity<BranchResponse> create(@RequestBody JsonNode body) {
        BranchResponse created = service.create(validator.command(body));
        return ResponseEntity.created(URI.create("/api/v1/branches/" + created.branchId())).body(created);
    }

    @PutMapping(path = "/api/v1/admin/branches/{branchId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace branch (ADMIN only)")
    public BranchResponse replace(@PathVariable String branchId, @RequestBody JsonNode body) {
        return service.replace(validator.pathId(branchId), validator.command(body));
    }

    @DeleteMapping("/api/v1/admin/branches/{branchId}")
    @Operation(summary = "Delete branch (ADMIN only)")
    public ResponseEntity<Void> delete(@PathVariable String branchId) {
        service.delete(validator.pathId(branchId));
        return ResponseEntity.noContent().build();
    }
}
