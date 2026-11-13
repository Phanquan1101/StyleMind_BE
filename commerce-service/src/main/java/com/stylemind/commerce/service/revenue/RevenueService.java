package com.stylemind.commerce.service.revenue;

import com.stylemind.commerce.dto.revenue.RevenueReportResponse;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.RevenueQuery;
import java.time.LocalDate;

public interface RevenueService {

    /** Shop-local "today" used for defaults and for rejecting future dates. */
    LocalDate today();

    RevenueReportResponse report(RevenueQuery query);

    /** UTF-8 (BOM) CSV of the period table, same numbers as {@link #report}. */
    byte[] exportCsv(RevenueQuery query);
}
