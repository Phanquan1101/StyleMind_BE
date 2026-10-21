package com.stylemind.wardrobecommunity.controller.outfit;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitDetailResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.OutfitSummaryResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.outfit.OutfitService;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/outfits")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class OutfitController {

    private final OutfitService service;
    private final OutfitRequestValidator validator;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the caller's outfits, most recently updated first")
    public OutfitListResponse list(Authentication authentication, HttpServletRequest request) {
        return service.list(owner(authentication), validator.list(request));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an outfit from 1 to 10 purchasable variants")
    public ResponseEntity<OutfitDetailResponse> create(Authentication authentication, @RequestBody JsonNode body) {
        var command = validator.create(body);
        OutfitDetailResponse created = service.create(owner(authentication), command);
        return ResponseEntity.created(URI.create("/api/v1/me/outfits/" + created.outfitId())).body(created);
    }

    @GetMapping(path = "/{outfitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an owned outfit with current product data")
    public OutfitDetailResponse get(Authentication authentication, @PathVariable String outfitId) {
        return service.get(owner(authentication), validator.pathId(outfitId));
    }

    @PatchMapping(path = "/{outfitId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Rename an owned outfit")
    public OutfitSummaryResponse rename(Authentication authentication, @PathVariable String outfitId,
            @RequestBody JsonNode body) {
        UUID id = validator.pathId(outfitId);
        return service.rename(owner(authentication), id, validator.rename(body));
    }

    @DeleteMapping("/{outfitId}")
    @Operation(summary = "Delete an owned outfit")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable String outfitId) {
        service.delete(owner(authentication), validator.pathId(outfitId));
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/{outfitId}/items", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add a purchasable variant to an owned outfit")
    public ResponseEntity<OutfitDetailResponse> addItem(Authentication authentication,
            @PathVariable String outfitId, @RequestBody JsonNode body) {
        UUID id = validator.pathId(outfitId);
        UUID variantId = validator.variantId(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addItem(owner(authentication), id, variantId));
    }

    @PatchMapping(path = "/{outfitId}/items/{outfitItemId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Switch an item to another variant (color or size) of the same product")
    public OutfitDetailResponse changeItem(Authentication authentication, @PathVariable String outfitId,
            @PathVariable String outfitItemId, @RequestBody JsonNode body) {
        UUID id = validator.pathId(outfitId);
        UUID itemId = validator.pathId(outfitItemId);
        return service.changeItemVariant(owner(authentication), id, itemId, validator.variantId(body));
    }

    @DeleteMapping("/{outfitId}/items/{outfitItemId}")
    @Operation(summary = "Remove an item from an owned outfit (the last item cannot be removed)")
    public ResponseEntity<Void> removeItem(Authentication authentication, @PathVariable String outfitId,
            @PathVariable String outfitItemId) {
        service.removeItem(owner(authentication), validator.pathId(outfitId), validator.pathId(outfitItemId));
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
