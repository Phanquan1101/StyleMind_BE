package com.stylemind.wardrobecommunity.controller.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.PublicLinkResponse;
import com.stylemind.wardrobecommunity.dto.outfit.ShareListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.ShareResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.outfit.OutfitShareService;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitRequestValidator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import lombok.RequiredArgsConstructor;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/outfits/{outfitId}")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OutfitShareController {

    private final OutfitShareService service;
    private final OutfitRequestValidator ids;

    @PostMapping(
            path = "/shares",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Retired: individual email sharing returns 410; use Friends and visibility",
            deprecated = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "410",
            description = "OUTFIT_EMAIL_SHARING_RETIRED after owner check")
    public ResponseEntity<ShareResponse> share(
            Authentication authentication,
            @PathVariable String outfitId,
            @RequestBody(required = false) String body) {
        UUID id = ids.pathId(outfitId);
        OutfitShareService.ShareResult result = service.share(owner(authentication), id, null);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.share());
    }

    @GetMapping(path = "/shares", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Retired: email grant listing returns 410", deprecated = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "410",
            description = "OUTFIT_EMAIL_SHARING_RETIRED after owner check")
    public ShareListResponse list(Authentication authentication, @PathVariable String outfitId) {
        return service.list(owner(authentication), ids.pathId(outfitId));
    }

    @DeleteMapping("/shares/{shareId}")
    @Operation(summary = "Retired: individual email revoke returns 410", deprecated = true)
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "410",
            description = "OUTFIT_EMAIL_SHARING_RETIRED after owner check")
    public ResponseEntity<Void> revoke(
            Authentication authentication,
            @PathVariable String outfitId,
            @PathVariable String shareId) {
        service.revoke(owner(authentication), ids.pathId(outfitId), ids.pathId(shareId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/public-link", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Choose PUBLIC and return the reusable public link")
    public ResponseEntity<PublicLinkResponse> enablePublicLink(
            Authentication authentication, @PathVariable String outfitId) {
        PublicLinkResponse link =
                service.enablePublicLink(owner(authentication), ids.pathId(outfitId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(link);
    }

    @DeleteMapping("/public-link")
    @Operation(summary = "If PUBLIC choose PRIVATE, keeping the token; otherwise no-op")
    public ResponseEntity<Void> disablePublicLink(
            Authentication authentication, @PathVariable String outfitId) {
        service.disablePublicLink(owner(authentication), ids.pathId(outfitId));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private UUID owner(Authentication authentication) {
        UUID ownerId = CurrentUser.subject(authentication);
        if (ownerId == null) {
            throw new ApiOperationException(
                    HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return ownerId;
    }
}
