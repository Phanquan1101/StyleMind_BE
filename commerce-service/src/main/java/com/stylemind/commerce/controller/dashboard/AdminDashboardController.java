package com.stylemind.commerce.controller.dashboard;

import com.stylemind.commerce.dto.dashboard.DashboardResponse;
import com.stylemind.commerce.service.dashboard.DashboardService;
import com.stylemind.commerce.validation.dashboard.DashboardRequestValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (SecurityConfiguration: /api/v1/admin/** requires ROLE_ADMIN). */
@RestController
@RequiredArgsConstructor
public class AdminDashboardController {

    private final DashboardService service;
    private final DashboardRequestValidator validator;

    @GetMapping(path = "/api/v1/admin/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DashboardResponse> dashboard(HttpServletRequest request) {
        DashboardResponse body = service.dashboard(validator.range(request, service.today()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
