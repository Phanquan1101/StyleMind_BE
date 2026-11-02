package com.stylemind.identity.controller.internal;

import com.stylemind.identity.dto.internal.InternalAccountLookupRequest;
import com.stylemind.identity.dto.internal.InternalAccountLookupResponse;
import com.stylemind.identity.service.internal.InternalAccountLookupService;
import com.stylemind.identity.validation.internal.InternalLookupRequestValidator;
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
public class InternalAccountController {

    private final InternalAccountLookupService service;
    private final InternalLookupRequestValidator validator;

    @PostMapping(path = "/internal/v1/accounts/batch-lookup", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public InternalAccountLookupResponse batchLookup(@RequestBody InternalAccountLookupRequest request) {
        return service.lookup(validator.accountIds(request));
    }
}
