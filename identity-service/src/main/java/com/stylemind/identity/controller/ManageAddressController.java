package com.stylemind.identity.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.identity.dto.AddressListResponse;
import com.stylemind.identity.dto.AddressResponse;
import com.stylemind.identity.service.ManageAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/addresses")
@SecurityRequirement(name = "bearerAuth")
public class ManageAddressController {
    private static final Set<String> OWNER_QUERY_FIELDS = Set.of("account_id", "customer_id", "user_id");
    private final ManageAddressService service;

    public ManageAddressController(ManageAddressService service) { this.service = service; }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the authenticated account's addresses", responses = {
            @ApiResponse(responseCode = "200", description = "Address list"),
            @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
            @ApiResponse(responseCode = "403", description = "CUSTOMER or ADMIN required")})
    public ResponseEntity<AddressListResponse> list(@AuthenticationPrincipal Jwt principal,
            @RequestParam Map<String, String> query) {
        rejectOwnerQuery(query);
        return noStore(service.list(subject(principal)));
    }

    @GetMapping(path = "/{addressId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get an owned address", responses = {
            @ApiResponse(responseCode = "200", description = "Address"),
            @ApiResponse(responseCode = "400", description = "Malformed address UUID or owner override"),
            @ApiResponse(responseCode = "404", description = "Address not found")})
    public ResponseEntity<AddressResponse> get(@AuthenticationPrincipal Jwt principal,
            @PathVariable String addressId, @RequestParam Map<String, String> query) {
        rejectOwnerQuery(query);
        return noStore(service.get(subject(principal), parseUuid(addressId)));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create an address for the authenticated account", responses = {
            @ApiResponse(responseCode = "201", description = "Address created"),
            @ApiResponse(responseCode = "400", description = "Invalid address request")})
    public ResponseEntity<AddressResponse> create(@AuthenticationPrincipal Jwt principal,
            @RequestBody JsonNode body, @RequestParam Map<String, String> query) {
        rejectOwnerQuery(query);
        AddressResponse created = service.create(subject(principal), body);
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .location(URI.create("/api/v1/me/addresses/" + created.addressId())).body(created);
    }

    @PatchMapping(path = "/{addressId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Patch an owned address", responses = {
            @ApiResponse(responseCode = "200", description = "Address updated"),
            @ApiResponse(responseCode = "400", description = "Invalid address request"),
            @ApiResponse(responseCode = "404", description = "Address not found")})
    public ResponseEntity<AddressResponse> patch(@AuthenticationPrincipal Jwt principal,
            @PathVariable String addressId, @RequestBody JsonNode body,
            @RequestParam Map<String, String> query) {
        rejectOwnerQuery(query);
        return noStore(service.patch(subject(principal), parseUuid(addressId), body));
    }

    @PutMapping(path = "/{addressId}/default", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Set an owned address as default", responses = {
            @ApiResponse(responseCode = "200", description = "Default address selected"),
            @ApiResponse(responseCode = "404", description = "Address not found")})
    public ResponseEntity<AddressResponse> setDefault(@AuthenticationPrincipal Jwt principal,
            @PathVariable String addressId, @RequestBody(required = false) String body,
            @RequestParam Map<String, String> query) {
        rejectOwnerQuery(query);
        if (body != null && !body.isEmpty()) throw invalidRequest();
        return noStore(service.setDefault(subject(principal), parseUuid(addressId)));
    }

    @DeleteMapping(path = "/{addressId}")
    @Operation(summary = "Delete an owned address", responses = {
            @ApiResponse(responseCode = "204", description = "Address deleted"),
            @ApiResponse(responseCode = "404", description = "Address not found"),
            @ApiResponse(responseCode = "409", description = "Choose another default first")})
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt principal,
            @PathVariable String addressId, @RequestParam Map<String, String> query) {
        rejectOwnerQuery(query);
        service.delete(subject(principal), parseUuid(addressId));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    private static ResponseEntity<AddressResponse> noStore(AddressResponse body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    private static ResponseEntity<AddressListResponse> noStore(AddressListResponse body) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }

    private static void rejectOwnerQuery(Map<String, String> query) {
        if (query.keySet().stream().anyMatch(OWNER_QUERY_FIELDS::contains)) throw invalidRequest();
    }

    private static UUID parseUuid(String value) {
        if (value == null || !value.matches("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            throw invalidRequest();
        }
        try { return UUID.fromString(value); } catch (IllegalArgumentException exception) { throw invalidRequest(); }
    }

    private static UUID subject(Jwt principal) { return UUID.fromString(principal.getSubject()); }

    private static com.stylemind.identity.errors.ApiOperationException invalidRequest() {
        return new com.stylemind.identity.errors.ApiOperationException(HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST", "Request is missing or malformed", 0);
    }
}
