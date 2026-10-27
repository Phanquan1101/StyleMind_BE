package com.stylemind.commerce.controller.internal;

import com.stylemind.commerce.dto.internal.InternalVariantLookupRequest;
import com.stylemind.commerce.dto.internal.InternalVariantLookupResponse;
import com.stylemind.commerce.service.internal.InternalVariantLookupService;
import com.stylemind.commerce.validation.internal.InternalLookupRequestValidator;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints protected by X-StyleMind-Service-Token; not part of the public API. */
@Hidden
@RestController
@RequiredArgsConstructor
public class InternalVariantController {

    private final InternalVariantLookupService service;
    private final InternalLookupRequestValidator validator;

    @PostMapping(path = "/internal/v1/variants/batch-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InternalVariantLookupResponse batchLookup(@RequestBody InternalVariantLookupRequest request) {
        return service.lookup(validator.variantIds(request));
    }
}
