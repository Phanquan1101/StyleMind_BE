package com.stylemind.commerce.service.dashboard;

import com.stylemind.commerce.dto.dashboard.DashboardResponse;
import com.stylemind.commerce.validation.dashboard.DashboardRequestValidator.DashboardRange;
import java.time.LocalDate;

public interface DashboardService {

    /** Shop-local "today" used for defaults and for rejecting future dates. */
    LocalDate today();

    DashboardResponse dashboard(DashboardRange range);
}
