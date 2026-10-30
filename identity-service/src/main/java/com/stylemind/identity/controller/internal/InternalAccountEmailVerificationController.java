package com.stylemind.identity.controller.internal;

import com.stylemind.identity.dto.internal.AccountEmailVerificationLookupResponse;
import com.stylemind.identity.dto.internal.InternalAccountLookupRequest;
import com.stylemind.identity.service.internal.AccountEmailVerificationLookupService;
import com.stylemind.identity.validation.internal.InternalLookupRequestValidator;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Same service-token boundary as the existing directory; not a public or frontend API. */
@Hidden
@RestController
@RequiredArgsConstructor
public class InternalAccountEmailVerificationController {
    private final AccountEmailVerificationLookupService service;
    private final InternalLookupRequestValidator validator;
    private final com.stylemind.identity.validation.internal.EmailLookupRequestValidator emailValidator;

    @PostMapping(path = "/internal/v1/accounts/email-verification-lookup-by-email",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountEmailVerificationLookupResponse> lookupByEmail(@RequestBody String body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.lookupByEmail(emailValidator.email(body)));
    }

    @PostMapping(path = "/internal/v1/accounts/email-verification-lookup",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AccountEmailVerificationLookupResponse> lookup(
            @RequestBody InternalAccountLookupRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.lookup(validator.accountIds(request)));
    }
}
