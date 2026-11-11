package com.stylemind.commerce.controller.returns;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.returns.RefundSource;
import com.stylemind.commerce.service.returns.RefundSourceService;
import com.stylemind.commerce.validation.returns.ReturnRequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InternalRefundController {

    private final RefundSourceService service;
    private final ReturnRequestValidator validator;

    @GetMapping("/internal/v1/refund-sources/{sourceType}/{sourceId}")
    public ResponseEntity<RefundSource> get(@PathVariable String sourceType, @PathVariable String sourceId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.get(validator.sourceType(sourceType), validator.pathId(sourceId)));
    }

    @PostMapping(path = "/internal/v1/refund-results", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> complete(@RequestBody JsonNode body) {
        service.complete(validator.refundResult(body));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
