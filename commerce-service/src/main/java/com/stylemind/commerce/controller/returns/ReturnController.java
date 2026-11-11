package com.stylemind.commerce.controller.returns;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.returns.ReturnResponse;
import com.stylemind.commerce.dto.returns.ReturnResponse.AdminPage;
import com.stylemind.commerce.dto.returns.ReturnResponse.OrderReturns;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.security.CurrentUser;
import com.stylemind.commerce.service.returns.ReturnService;
import com.stylemind.commerce.validation.returns.ReturnEvidenceValidator;
import com.stylemind.commerce.validation.returns.ReturnRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class ReturnController {

    private final ReturnService service;
    private final ReturnRequestValidator requests;
    private final ReturnEvidenceValidator evidence;

    @GetMapping("/api/v1/orders/{orderId}/returns")
    public OrderReturns forOrder(Authentication authentication, @PathVariable String orderId) {
        return service.forOrder(subject(authentication), requests.pathId(orderId));
    }

    @PostMapping(path = "/api/v1/orders/{orderId}/returns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReturnResponse> create(Authentication authentication, @PathVariable String orderId,
            @RequestPart("request") byte[] request,
            @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        ReturnResponse result = service.create(subject(authentication), requests.pathId(orderId),
                requests.create(request), evidence.validate(images));
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PostMapping(path = "/api/v1/returns/{id}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReturnResponse addEvidence(Authentication authentication, @PathVariable String id,
            @RequestPart(name = "images", required = false) List<MultipartFile> images) {
        return service.addEvidence(subject(authentication), requests.pathId(id), evidence.validate(images));
    }

    @GetMapping("/api/v1/returns/{id}/evidence/{imageId}")
    public ResponseEntity<byte[]> evidence(Authentication authentication, @PathVariable String id,
            @PathVariable String imageId) {
        UUID actor = subject(authentication);
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
        var image = service.evidence(actor, admin, requests.pathId(id), requests.pathId(imageId));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(image.contentType())).body(image.bytes());
    }

    @GetMapping("/api/v1/admin/returns")
    public AdminPage adminPage(HttpServletRequest request) {
        var page = requests.pagination(request.getParameterMap());
        return service.adminPage(page.page(), page.size(), page.offset());
    }

    @PostMapping(path = "/api/v1/admin/returns/{id}/decision", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ReturnResponse decide(Authentication authentication, @PathVariable String id, @RequestBody JsonNode body) {
        return service.decide(subject(authentication), requests.pathId(id), requests.decision(body));
    }

    private UUID subject(Authentication authentication) {
        UUID subject = CurrentUser.subject(authentication);
        if (subject == null) {
            throw new ApiOperationException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required");
        }
        return subject;
    }
}
