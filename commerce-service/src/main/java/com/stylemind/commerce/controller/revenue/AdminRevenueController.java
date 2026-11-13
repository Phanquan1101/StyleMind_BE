package com.stylemind.commerce.controller.revenue;

import com.stylemind.commerce.dto.revenue.RevenueReportResponse;
import com.stylemind.commerce.service.revenue.RevenueService;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.RevenueQuery;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only (SecurityConfiguration: /api/v1/admin/revenue** requires ROLE_ADMIN). */
@RestController
@RequiredArgsConstructor
public class AdminRevenueController {

    private final RevenueService service;
    private final RevenueRequestValidator validator;

    @GetMapping(path = "/api/v1/admin/revenue", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RevenueReportResponse> report(HttpServletRequest request) {
        RevenueQuery query = validator.query(request, service.today());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.report(query));
    }

    @GetMapping(path = "/api/v1/admin/revenue/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> export(HttpServletRequest request) {
        RevenueQuery query = validator.query(request, service.today());
        String filename = "doanh-thu_" + query.from() + "_" + query.to() + "_" + query.group().wire() + ".csv";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(service.exportCsv(query));
    }
}
