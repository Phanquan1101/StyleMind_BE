package com.stylemind.wardrobecommunity.controller.outfit;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.wardrobecommunity.dto.outfit.PublicLinkResponse;
import com.stylemind.wardrobecommunity.dto.outfit.ShareListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.ShareResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.outfit.OutfitShareService;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitRequestValidator;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitShareRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/v1/me/outfits/{outfitId}")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OutfitShareController {

    private final OutfitShareService service;
    private final OutfitRequestValidator ids;
    private final OutfitShareRequestValidator validator;

    @PostMapping(path = "/shares", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Share an owned outfit with an email address (201 new or re-granted, 200 already shared)")
    public ResponseEntity<ShareResponse> share(Authentication authentication, @PathVariable String outfitId,
            @RequestBody JsonNode body) {
        UUID id = ids.pathId(outfitId);
        String email = validator.email(body);
        OutfitShareService.ShareResult result = service.share(owner(authentication), id, email);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.share());
    }

    @GetMapping(path = "/shares", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the active shares of an owned outfit")
    public ShareListResponse list(Authentication authentication, @PathVariable String outfitId) {
        return service.list(owner(authentication), ids.pathId(outfitId));
    }

    @DeleteMapping("/shares/{shareId}")
    @Operation(summary = "Revoke an active share")
    public ResponseEntity<Void> revoke(Authentication authentication, @PathVariable String outfitId,
            @PathVariable String shareId) {
        service.revoke(owner(authentication), ids.pathId(outfitId), ids.pathId(shareId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping(path = "/public-link", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Turn on the public link (returns the current token when it is already on)")
    public ResponseEntity<PublicLinkResponse> enablePublicLink(Authentication authentication,
            @PathVariable String outfitId) {
        PublicLinkResponse link = service.enablePublicLink(owner(authentication), ids.pathId(outfitId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(link);
    }

    @DeleteMapping("/public-link")
    @Operation(summary = "Turn off the public link and drop its token")
    public ResponseEntity<Void> disablePublicLink(Authentication authentication, @PathVariable String outfitId) {
        service.disablePublicLink(owner(authentication), ids.pathId(outfitId));
        return ResponseEntity.noContent().build();
    }

    private UUID owner(Authentication authentication) {
        UUID ownerId = CurrentUser.subject(authentication);
        if (ownerId == null) {
            throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return ownerId;
    }
}
