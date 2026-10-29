package com.stylemind.identity.controller;

import com.stylemind.identity.dto.users.ManagedUserRolesResponse;
import com.stylemind.identity.dto.users.ManagedUserStatusResponse;
import com.stylemind.identity.dto.users.ManageUsersListRequest;
import com.stylemind.identity.dto.users.ManageUsersPageResponse;
import com.stylemind.identity.service.AdminUserService;
import com.stylemind.identity.validation.users.ManageUsersRequestValidator;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService service;
    private final ManageUsersRequestValidator validator;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List and filter users (ADMIN only)",
            description = "Revalidates the current actor account, ACTIVE status, email verification and ADMIN role "
                    + "in the Identity database. Filters combine with AND; order is fixed and stable. Responses are no-store.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User page"),
                    @ApiResponse(responseCode = "400", description = "Invalid filter or pagination"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Current eligible ADMIN required")
            })
    public ResponseEntity<ManageUsersPageResponse> list(
            @AuthenticationPrincipal Jwt principal,
            HttpServletRequest request,
            @Parameter(description = "Exact status: ACTIVE, PENDING_VERIFICATION, or SUSPENDED")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String status,
            @Parameter(description = "Exact role: CUSTOMER or ADMIN")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String role,
            @Parameter(name = "email_contains", description = "Case-insensitive literal email substring")
            @org.springframework.web.bind.annotation.RequestParam(name = "email_contains", required = false) String emailContains,
            @Parameter(description = "Zero-based page; default 0")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String page,
            @Parameter(description = "Page size 1–100; default 20")
            @org.springframework.web.bind.annotation.RequestParam(required = false) String size) {
        ManageUsersListRequest criteria = validator.listCriteria(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(subject(principal), criteria));
    }

    @PatchMapping(path = "/{accountId}/status", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Suspend or reactivate an account (ADMIN only)",
            description = "Only ACTIVE↔SUSPENDED transitions are supported. Pending verification cannot be bypassed. "
                    + "No-op is idempotent and has no data/session side effect. Status changes do not revoke refresh sessions.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Status unchanged or updated"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID or request"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Current eligible ADMIN required"),
                    @ApiResponse(responseCode = "404", description = "Target account not found"),
                    @ApiResponse(responseCode = "409", description = "Transition, self-change, or last-Admin guard")
            })
    public ResponseEntity<ManagedUserStatusResponse> updateStatus(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String accountId,
            @RequestBody JsonNode body) {
        UUID targetId = validator.accountId(accountId);
        String status = validator.requestedStatus(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updateStatus(subject(principal), targetId, status));
    }

    @PutMapping(path = "/{accountId}/roles", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace an account's complete role set (ADMIN only)",
            description = "Accepts a nonempty duplicate-free set of CUSTOMER and ADMIN. Both roles may coexist. "
                    + "Self-change and last qualifying Admin guards apply. No sessions are revoked.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Roles unchanged or replaced"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID or request"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "Current eligible ADMIN required"),
                    @ApiResponse(responseCode = "404", description = "Target account not found"),
                    @ApiResponse(responseCode = "409", description = "Self-change or last-Admin guard")
            })
    public ResponseEntity<ManagedUserRolesResponse> replaceRoles(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable String accountId,
            @RequestBody JsonNode body) {
        UUID targetId = validator.accountId(accountId);
        Set<String> roles = validator.requestedRoles(body);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.replaceRoles(subject(principal), targetId, roles));
    }

    private UUID subject(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
