package com.stylemind.wardrobecommunity.controller.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.*;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.outfit.SharedOutfitService;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitRequestValidator;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/friends/{friendAccountId}/outfits")
@Tag(name = "Friend Outfits")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: UUID or pagination"),
    @ApiResponse(responseCode = "401", description = "A valid JWT is required"),
    @ApiResponse(responseCode = "403", description = "CUSTOMER required"),
    @ApiResponse(
            responseCode = "404",
            description = "Not current friends, private/unknown outfit or wrong parent"),
    @ApiResponse(
            responseCode = "503",
            description = "Identity/catalog unavailable; no authorization fallback")
})
@RequiredArgsConstructor
public class FriendOutfitController {
    private final SharedOutfitService service;
    private final OutfitRequestValidator validator;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "List a current friend's FRIENDS/PUBLIC outfits",
            description =
                    "PRIVATE outfits are absent from both items and total counts. Updated newest"
                        + " first, then outfit ID.")
    @Parameters({
        @Parameter(
                name = "page",
                in = ParameterIn.QUERY,
                schema = @Schema(type = "integer", defaultValue = "0", minimum = "0")),
        @Parameter(
                name = "size",
                in = ParameterIn.QUERY,
                schema =
                        @Schema(
                                type = "integer",
                                defaultValue = "20",
                                minimum = "1",
                                maximum = "100"))
    })
    public ResponseEntity<FriendOutfitListResponse> list(
            Authentication authentication,
            @PathVariable String friendAccountId,
            HttpServletRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        service.friendsList(
                                actor(authentication),
                                validator.pathId(friendAccountId),
                                validator.list(request)));
    }

    @GetMapping(path = "/{outfitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "View one FRIENDS/PUBLIC outfit of a current friend",
            description =
                    "View only; current friendship and visibility are rechecked after downstream"
                        + " I/O.")
    public ResponseEntity<SharedOutfitViewResponse> get(
            Authentication authentication,
            @PathVariable String friendAccountId,
            @PathVariable String outfitId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        service.friendView(
                                actor(authentication),
                                validator.pathId(friendAccountId),
                                validator.pathId(outfitId)));
    }

    private UUID actor(Authentication authentication) {
        UUID actor = CurrentUser.subject(authentication);
        if (actor == null)
            throw new ApiOperationException(
                    HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        return actor;
    }
}
