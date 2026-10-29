package com.stylemind.commerce.controller.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.stylemind.commerce.dto.catalog.CatalogReferenceListResponse;
import com.stylemind.commerce.dto.catalog.CatalogReferenceResponse;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.dto.catalog.CatalogReferenceRequest;
import com.stylemind.commerce.enums.CatalogReferenceType;
import com.stylemind.commerce.service.catalog.CatalogReferenceService;
import com.stylemind.commerce.validation.catalog.CatalogReferenceRequestValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class BrandController {

    private final CatalogReferenceService service;
    private final CatalogReferenceRequestValidator validator;

    @GetMapping(path = "/api/v1/brands", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List brands",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Brand list"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid")
            })
    public CatalogReferenceListResponse list() {
        return service.list(CatalogReferenceType.BRAND);
    }

    @GetMapping(path = "/api/v1/brands/{brandId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get brand",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Brand"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "404", description = "Brand not found")
            })
    public CatalogReferenceResponse get(@PathVariable String brandId) {
        return service.get(CatalogReferenceType.BRAND, id(brandId));
    }

    @PostMapping(path = "/api/v1/admin/brands", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create brand (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Brand created"),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "ADMIN role required"),
                    @ApiResponse(responseCode = "409", description = "Duplicate slug")
            })
    public ResponseEntity<CatalogReferenceResponse> create(@RequestBody JsonNode body) {
        CatalogReferenceResponse created = service.create(CatalogReferenceType.BRAND, validator.command(body));
        return ResponseEntity.created(URI.create("/api/v1/brands/" + created.id())).body(created);
    }

    @PutMapping(path = "/api/v1/admin/brands/{brandId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace brand (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Brand replaced"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID or invalid request"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "ADMIN role required"),
                    @ApiResponse(responseCode = "404", description = "Brand not found"),
                    @ApiResponse(responseCode = "409", description = "Duplicate slug")
            })
    public CatalogReferenceResponse replace(@PathVariable String brandId, @RequestBody JsonNode body) {
        CatalogReferenceRequest command = validator.command(body);
        return service.replace(CatalogReferenceType.BRAND, id(brandId), command);
    }

    @DeleteMapping("/api/v1/admin/brands/{brandId}")
    @Operation(summary = "Delete brand (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Brand deleted"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "ADMIN role required"),
                    @ApiResponse(responseCode = "404", description = "Brand not found"),
                    @ApiResponse(responseCode = "409", description = "Brand is referenced by products")
            })
    public ResponseEntity<Void> delete(@PathVariable String brandId) {
        service.delete(CatalogReferenceType.BRAND, id(brandId));
        return ResponseEntity.noContent().build();
    }

    private UUID id(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            throw new ApiOperationException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST",
                    "Request is missing or malformed");
        }
    }
}
