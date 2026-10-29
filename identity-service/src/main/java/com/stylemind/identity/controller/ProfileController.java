package com.stylemind.identity.controller;

import com.stylemind.identity.dto.profile.MyProfileResponse;
import com.stylemind.identity.dto.profile.PatchMyProfileRequest;
import com.stylemind.identity.dto.profile.ReplaceStyleProfileRequest;
import com.stylemind.identity.dto.profile.StyleProfileResponse;
import com.stylemind.identity.dto.profile.StyleProfileUpdateRequest;
import com.stylemind.identity.service.ProfileService;
import com.stylemind.identity.validation.profile.ProfileRequestValidator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final ProfileRequestValidator validator;

    @GetMapping(path = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the authenticated account profile",
            description = "Requires CUSTOMER or ADMIN. Reads only the JWT subject account. The profile is null until "
                    + "display_name is first saved; this operation does not create data.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile read"),
                    @ApiResponse(responseCode = "401", description = "JWT is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "CUSTOMER or ADMIN role is required"),
                    @ApiResponse(responseCode = "404", description = "JWT subject account does not exist")
            })
    public MyProfileResponse getMyProfile(@AuthenticationPrincipal Jwt principal) {
        return profileService.getMyProfile(subject(principal));
    }

    @PatchMapping(path = "/me", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create or update the authenticated account display name",
            description = "Requires CUSTOMER or ADMIN and always targets JWT sub. Accepts only display_name. "
                    + "Leading/trailing Unicode whitespace is trimmed; "
                    + "the nonblank value is limited to 100 Unicode code points.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Profile saved"),
                    @ApiResponse(responseCode = "400", description = "Request is invalid"),
                    @ApiResponse(responseCode = "401", description = "JWT is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "CUSTOMER or ADMIN role is required"),
                    @ApiResponse(responseCode = "404", description = "JWT subject account does not exist")
            })
    public MyProfileResponse patchMyProfile(
            @AuthenticationPrincipal Jwt principal,
            @RequestBody PatchMyProfileRequest request) {
        return profileService.patchMyProfile(subject(principal), validator.validatePatch(request));
    }

    @GetMapping(path = "/me/style-profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get the authenticated account style profile",
            description = "Requires CUSTOMER or ADMIN. Reads only the JWT subject. An absent style_profile is "
                    + "returned as null without creating rows. A present profile returns all scalar fields and "
                    + "three deterministically sorted collections.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Style profile read"),
                    @ApiResponse(responseCode = "401", description = "JWT is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "CUSTOMER or ADMIN role is required"),
                    @ApiResponse(responseCode = "404", description = "JWT subject account does not exist")
            })
    public StyleProfileResponse getStyleProfile(@AuthenticationPrincipal Jwt principal) {
        return profileService.getStyleProfile(subject(principal));
    }

    @PutMapping(path = "/me/style-profile", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace the authenticated account style profile",
            description = "Requires CUSTOMER or ADMIN and always targets JWT sub. Requires all five nullable "
                    + "scalar properties and all three non-null collection properties. "
                    + "Empty arrays clear collections. Brand UUIDs are stored as logical references; "
                    + "Identity does not verify brand existence or active status in Commerce.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Style profile replaced"),
                    @ApiResponse(responseCode = "400", description = "Request is invalid"),
                    @ApiResponse(responseCode = "401", description = "JWT is missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "CUSTOMER or ADMIN role is required"),
                    @ApiResponse(responseCode = "404", description = "JWT subject account does not exist")
            })
    public StyleProfileResponse replaceStyleProfile(
            @AuthenticationPrincipal Jwt principal,
            @RequestBody ReplaceStyleProfileRequest request) {
        StyleProfileUpdateRequest validated = validator.validateReplacement(request);
        return profileService.replaceStyleProfile(subject(principal), validated);
    }

    private UUID subject(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
