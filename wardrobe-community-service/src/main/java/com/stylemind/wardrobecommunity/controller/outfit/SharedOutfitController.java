package com.stylemind.wardrobecommunity.controller.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.SharedOutfitListResponse;
import com.stylemind.wardrobecommunity.dto.outfit.SharedOutfitViewResponse;
import com.stylemind.wardrobecommunity.errors.ApiOperationException;
import com.stylemind.wardrobecommunity.security.CurrentUser;
import com.stylemind.wardrobecommunity.service.outfit.SharedOutfitService;
import com.stylemind.wardrobecommunity.validation.outfit.OutfitRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/shared-outfits")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class SharedOutfitController {

    private final SharedOutfitService service;
    private final OutfitRequestValidator validator;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List outfits shared with the caller, newest share first")
    public ResponseEntity<SharedOutfitListResponse> list(Authentication authentication, HttpServletRequest request) {
        var criteria = validator.list(request);
        return noStore(service.list(caller(authentication), criteria));
    }

    @GetMapping(path = "/{outfitId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an outfit that is actively shared with the caller")
    public ResponseEntity<SharedOutfitViewResponse> get(Authentication authentication, @PathVariable String outfitId) {
        UUID id = validator.pathId(outfitId);
        return noStore(service.view(caller(authentication), id));
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private UUID caller(Authentication authentication) {
        UUID callerId = CurrentUser.subject(authentication);
        if (callerId == null) {
            throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return callerId;
    }
}
