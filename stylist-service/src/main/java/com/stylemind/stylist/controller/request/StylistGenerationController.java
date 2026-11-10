package com.stylemind.stylist.controller.request;

import com.stylemind.stylist.dto.generation.GenerateRecommendationsResponse;
import com.stylemind.stylist.dto.generation.GenerateRequest;
import com.stylemind.stylist.dto.generation.RecommendationDetailResponse;
import com.stylemind.stylist.dto.generation.RequestRecommendationsResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.service.generation.StylistGenerationService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/stylist")
public class StylistGenerationController {
    private final StylistGenerationService generationService;

    public StylistGenerationController(StylistGenerationService generationService) {
        this.generationService = generationService;
    }

    @PostMapping(value = "/requests/{requestId}/generate", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Generate recommendations for an owned request")
    public ResponseEntity<GenerateRecommendationsResponse> generate(
            @PathVariable String requestId, @RequestBody GenerateRequest body, @AuthenticationPrincipal Jwt jwt) {
        UUID owner = subject(jwt);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(generationService.generate(owner, parseUuid(requestId)));
    }

    @GetMapping(value = "/requests/{requestId}/recommendations", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read recommendations for an owned request without starting generation")
    public ResponseEntity<RequestRecommendationsResponse> readRequestRecommendations(
            @PathVariable String requestId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireNoQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                generationService.readRequestRecommendations(subject(jwt), parseUuid(requestId)));
    }

    @GetMapping(value = "/recommendations/{recommendationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an owned recommendation with current Commerce item views")
    public ResponseEntity<RecommendationDetailResponse> readRecommendation(
            @PathVariable String recommendationId, @AuthenticationPrincipal Jwt jwt, HttpServletRequest request) {
        requireNoQuery(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                generationService.readRecommendation(subject(jwt), parseUuid(recommendationId)));
    }

    private static void requireNoQuery(HttpServletRequest request) {
        if (request.getQueryString() != null) throw new StylistApiException(ErrorCode.INVALID_REQUEST);
    }

    private static UUID subject(Jwt jwt) {
        try { return UUID.fromString(jwt.getSubject()); }
        catch (RuntimeException exception) { throw new StylistApiException(ErrorCode.INVALID_REQUEST); }
    }

    private static UUID parseUuid(String raw) {
        try {
            UUID parsed = UUID.fromString(raw);
            if (!parsed.toString().equalsIgnoreCase(raw)) throw new IllegalArgumentException();
            return parsed;
        } catch (IllegalArgumentException exception) {
            throw new StylistApiException(ErrorCode.INVALID_REQUEST);
        }
    }
}
