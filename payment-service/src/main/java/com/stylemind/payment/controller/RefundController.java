package com.stylemind.payment.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.payment.dto.RefundRequests.Completion;
import com.stylemind.payment.dto.RefundRequests.Destination;
import com.stylemind.payment.dto.RefundRequests.Failure;
import com.stylemind.payment.dto.RefundResponse;
import com.stylemind.payment.dto.RefundSourceType;
import com.stylemind.payment.service.PaymentAuthorizationService;
import com.stylemind.payment.service.RefundService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RefundController {

    private static final String PUBLIC = "/api/v1/refunds/{sourceType}/{sourceId}";
    private static final String ADMIN = "/api/v1/admin/refunds/{sourceType}/{sourceId}";

    private final RefundService refunds;
    private final PaymentAuthorizationService authorization;

    public RefundController(RefundService refunds, PaymentAuthorizationService authorization) {
        this.refunds = refunds;
        this.authorization = authorization;
    }

    @PostMapping(PUBLIC + "/prepare")
    ResponseEntity<RefundResponse> prepare(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt) {
        return noStore(refunds.prepare(sourceType, RefundSourceType.parseId(sourceId), authorization.refundActor(jwt)));
    }

    @GetMapping(PUBLIC)
    ResponseEntity<RefundResponse> get(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt) {
        return noStore(refunds.get(sourceType, RefundSourceType.parseId(sourceId), authorization.refundActor(jwt)));
    }

    @PutMapping(path = PUBLIC + "/destination", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RefundResponse> destination(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return noStore(refunds.saveDestination(sourceType, RefundSourceType.parseId(sourceId),
                authorization.refundActor(jwt), Destination.parse(body)));
    }

    @PostMapping(ADMIN + "/start")
    ResponseEntity<RefundResponse> start(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt) {
        return noStore(refunds.start(sourceType, RefundSourceType.parseId(sourceId), authorization.adminSubject(jwt)));
    }

    @GetMapping(ADMIN + "/destination")
    ResponseEntity<Destination> getDestination(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt) {
        return noStore(refunds.getDestination(sourceType, RefundSourceType.parseId(sourceId), authorization.adminSubject(jwt)));
    }

    @PostMapping(path = ADMIN + "/complete", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RefundResponse> complete(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return noStore(refunds.complete(sourceType, RefundSourceType.parseId(sourceId), authorization.adminSubject(jwt),
                Completion.parse(body)));
    }

    @PostMapping(path = ADMIN + "/fail", consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RefundResponse> fail(@PathVariable RefundSourceType sourceType, @PathVariable String sourceId,
            @AuthenticationPrincipal Jwt jwt, @RequestBody JsonNode body) {
        return noStore(refunds.fail(sourceType, RefundSourceType.parseId(sourceId), authorization.adminSubject(jwt),
                Failure.parse(body)));
    }

    private static <T> ResponseEntity<T> noStore(T response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
