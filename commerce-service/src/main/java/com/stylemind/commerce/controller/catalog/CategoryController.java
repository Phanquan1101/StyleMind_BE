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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class CategoryController {

    private final CatalogReferenceService service;
    private final CatalogReferenceRequestValidator validator;

    @GetMapping(path = "/api/v1/categories", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List categories",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category list"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid")
            })
    public CatalogReferenceListResponse list() {
        return service.list(CatalogReferenceType.CATEGORY);
    }

    @GetMapping(path = "/api/v1/categories/{categoryId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get category",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "404", description = "Category not found")
            })
    public CatalogReferenceResponse get(@PathVariable String categoryId) {
        return service.get(CatalogReferenceType.CATEGORY, id(categoryId));
    }

    @PostMapping(path = "/api/v1/admin/categories", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create category (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Category created"),
                    @ApiResponse(responseCode = "400", description = "Invalid request"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "ADMIN role required"),
                    @ApiResponse(responseCode = "409", description = "Duplicate slug")
            })
    public ResponseEntity<CatalogReferenceResponse> create(@RequestBody JsonNode body) {
        CatalogReferenceResponse created = service.create(CatalogReferenceType.CATEGORY, validator.command(body));
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }

    @PutMapping(path = "/api/v1/admin/categories/{categoryId}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Replace category (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Category replaced"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID or invalid request"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "ADMIN role required"),
                    @ApiResponse(responseCode = "404", description = "Category not found"),
                    @ApiResponse(responseCode = "409", description = "Duplicate slug")
            })
    public CatalogReferenceResponse replace(@PathVariable String categoryId, @RequestBody JsonNode body) {
        CatalogReferenceRequest command = validator.command(body);
        return service.replace(CatalogReferenceType.CATEGORY, id(categoryId), command);
    }

    @DeleteMapping("/api/v1/admin/categories/{categoryId}")
    @Operation(summary = "Delete category (ADMIN only)",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Category deleted"),
                    @ApiResponse(responseCode = "400", description = "Malformed UUID"),
                    @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
                    @ApiResponse(responseCode = "403", description = "ADMIN role required"),
                    @ApiResponse(responseCode = "404", description = "Category not found"),
                    @ApiResponse(responseCode = "409", description = "Category is referenced by products")
            })
    public ResponseEntity<Void> delete(@PathVariable String categoryId) {
        service.delete(CatalogReferenceType.CATEGORY, id(categoryId));
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
