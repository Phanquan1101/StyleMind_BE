package com.stylemind.stylist.controller.session;

import com.stylemind.stylist.dto.session.CreateStylistSessionRequest;
import com.stylemind.stylist.dto.session.SessionHistoryQuery;
import com.stylemind.stylist.dto.session.StylistMessagePageResponse;
import com.stylemind.stylist.dto.session.StylistSessionResponse;
import com.stylemind.stylist.service.session.SessionHistoryQueryValidator;
import com.stylemind.stylist.service.session.StylistSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/stylist/sessions")
@Tag(name = "Stylist sessions", description = "Create an owned session and read its newest-first message history")
public class StylistSessionController {

    private final StylistSessionService sessionService;
    private final SessionHistoryQueryValidator queryValidator;

    public StylistSessionController(StylistSessionService sessionService,
                                    SessionHistoryQueryValidator queryValidator) {
        this.sessionService = sessionService;
        this.queryValidator = queryValidator;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a Stylist session", description = "Creates an empty session owned by the JWT subject.")
    public ResponseEntity<StylistSessionResponse> createSession(
            @RequestBody CreateStylistSessionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        StylistSessionResponse response = sessionService.createSession(subject(jwt), request);
        URI location = URI.create("/stylist/api/v1/stylist/sessions/" + response.sessionId());
        return ResponseEntity.created(location)
                .cacheControl(CacheControl.noStore())
                .body(response);
    }

    @GetMapping(value = "/{sessionId}/messages", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Read session history", description = "Reads only the JWT owner's messages, newest first.")
    public ResponseEntity<StylistMessagePageResponse> readMessages(
            @PathVariable String sessionId,
            HttpServletRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID parsedSessionId = queryValidator.sessionId(sessionId);
        SessionHistoryQuery query = queryValidator.parse(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(sessionService.readMessages(subject(jwt), parsedSessionId, query));
    }

    private static UUID subject(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
