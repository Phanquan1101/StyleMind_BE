package com.stylemind.wardrobecommunity.controller.outfit;

import com.stylemind.wardrobecommunity.dto.outfit.SharedOutfitViewResponse;
import com.stylemind.wardrobecommunity.service.outfit.SharedOutfitService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** No authentication: possession of the unguessable token is the permission. */
@RestController
@RequestMapping("/api/v1/public/outfits")
@RequiredArgsConstructor
public class PublicOutfitController {

    private final SharedOutfitService service;

    @GetMapping(path = "/{publicToken}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an outfit through its public link token")
    public ResponseEntity<SharedOutfitViewResponse> get(@PathVariable String publicToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.publicView(publicToken));
    }
}
