package com.stylemind.stylist.controller.request;

import com.stylemind.stylist.dto.request.CreateStylingRequestRequest;
import com.stylemind.stylist.dto.request.CreateStylingRequestResponse;
import com.stylemind.stylist.dto.request.StylingRequestResponse;
import com.stylemind.stylist.errors.ErrorCode;
import com.stylemind.stylist.errors.StylistApiException;
import com.stylemind.stylist.service.request.StylingRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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

@RestController
@RequestMapping("/api/v1/stylist")
@Tag(name = "Stylist requests", description = "Store a request as RECEIVED; no recommendation generation is started")
public class StylingRequestController {

    private final StylingRequestService stylingRequestService;

    public StylingRequestController(StylingRequestService stylingRequestService) {
        this.stylingRequestService = stylingRequestService;
    }

    @PostMapping(value = "/sessions/{sessionId}/requests", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Submit a styling request", description = "Persists a request only; generation is not run.")
    public ResponseEntity<CreateStylingRequestResponse> createRequest(
            @PathVariable String sessionId,
            @RequestBody CreateStylingRequestRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        CreateStylingRequestResponse response = stylingRequestService.createRequest(
                subject(jwt), parseUuid(sessionId), request);
        URI location = URI.create("/stylist/api/v1/stylist/requests/" + response.requestId());
        return ResponseEntity.created(location)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(response);
    }

    @GetMapping(value = "/requests/{requestId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read an owned styling request", description = "Returns stored request data; no generation runs.")
    public ResponseEntity<StylingRequestResponse> readRequest(
            @PathVariable String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(stylingRequestService.readRequest(subject(jwt), parseUuid(requestId)));
    }

    private static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
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
