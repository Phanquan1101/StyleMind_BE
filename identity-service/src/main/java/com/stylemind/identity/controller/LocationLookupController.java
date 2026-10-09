package com.stylemind.identity.controller;

import com.stylemind.identity.dto.ProvinceListResponse;
import com.stylemind.identity.dto.WardListResponse;
import com.stylemind.identity.service.ManageAddressService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/locations")
@SecurityRequirement(name = "bearerAuth")
public class LocationLookupController {
    private final ManageAddressService service;

    public LocationLookupController(ManageAddressService service) { this.service = service; }

    @GetMapping(path = "/provinces", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List pinned Vietnam provinces", responses = {
            @ApiResponse(responseCode = "200", description = "Province list"),
            @ApiResponse(responseCode = "401", description = "JWT missing or invalid"),
            @ApiResponse(responseCode = "403", description = "CUSTOMER or ADMIN required")})
    public ProvinceListResponse provinces() { return service.provinces(); }

    @GetMapping(path = "/provinces/{provinceCode}/wards", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List wards for a province code", responses = {
            @ApiResponse(responseCode = "200", description = "Ward list"),
            @ApiResponse(responseCode = "404", description = "Province not found")})
    public WardListResponse wards(@PathVariable String provinceCode) { return service.wards(provinceCode); }
}
