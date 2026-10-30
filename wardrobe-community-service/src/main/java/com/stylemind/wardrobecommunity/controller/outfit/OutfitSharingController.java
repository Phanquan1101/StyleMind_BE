package com.stylemind.wardrobecommunity.controller.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.OutfitSharingResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.outfit.OutfitShareService;
import com.stylemind.wardrobecommunity.validation.outfit.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/outfits/{outfitId}/sharing")
@Tag(name = "Outfit Sharing")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(
            responseCode = "400",
            description = "INVALID_REQUEST: strict visibility enum/body or UUID"),
    @ApiResponse(responseCode = "401", description = "A valid JWT is required"),
    @ApiResponse(responseCode = "403", description = "CUSTOMER required"),
    @ApiResponse(responseCode = "404", description = "Unknown outfit or caller is not its owner")
})
@RequiredArgsConstructor
public class OutfitSharingController {
    private final OutfitShareService service;
    private final OutfitRequestValidator ids;
    private final OutfitVisibilityRequestValidator validator;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read the owned outfit's visibility and public-link state")
    public ResponseEntity<OutfitSharingResponse> get(
            Authentication authentication, @PathVariable String outfitId) {
        return noStore(service.settings(owner(authentication), ids.pathId(outfitId)));
    }

    @PatchMapping(
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Choose PRIVATE, FRIENDS or PUBLIC for one owned outfit",
            description =
                    "FRIENDS permits all current friends. Invitations never change this setting."
                        + " Public OFF/ON keeps the same link.",
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            schema =
                                                    @Schema(
                                                            implementation =
                                                                    com.stylemind.wardrobecommunity
                                                                            .dto.outfit
                                                                            .OutfitVisibilityRequest
                                                                            .class),
                                            examples =
                                                    @ExampleObject(
                                                            value =
                                                                    "{\"visibility\":\"FRIENDS\"}"))))
    public ResponseEntity<OutfitSharingResponse> change(
            Authentication authentication,
            @PathVariable String outfitId,
            @RequestBody String body) {
        return noStore(
                service.changeVisibility(
                        owner(authentication), ids.pathId(outfitId), validator.visibility(body)));
    }

    private UUID owner(Authentication authentication) {
        UUID id = CurrentUser.subject(authentication);
        if (id == null)
            throw new ApiOperationException(
                    HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        return id;
    }

    private ResponseEntity<OutfitSharingResponse> noStore(OutfitSharingResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
