package com.stylemind.commerce.service.dashboard.impl;

import com.stylemind.commerce.dto.dashboard.DashboardResponse;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.DayPoint;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.Kpi;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.Kpis;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.MethodRevenue;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.Pending;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.RecentOrder;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.StatusCount;
import com.stylemind.commerce.dto.dashboard.DashboardResponse.TopProduct;
import com.stylemind.commerce.repository.dashboard.DashboardRepository;
import com.stylemind.commerce.repository.dashboard.DashboardRepository.PlacedRow;
import com.stylemind.commerce.repository.dashboard.DashboardRepository.RevenueRow;
import com.stylemind.commerce.service.dashboard.DashboardService;
import com.stylemind.commerce.validation.dashboard.DashboardRequestValidator.DashboardRange;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int LOW_STOCK_THRESHOLD = 5;
    /** Shown even when zero so the chart legend is stable. */
    private static final List<String> METHODS = List.of("VNPAY", "COD", "SEPAY_VIETQR");

    private final DashboardRepository repository;

    @Override
    public LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(DashboardRange range) {
        int days = range.days();
        LocalDate previousTo = range.from().minusDays(1);
        LocalDate previousFrom = previousTo.minusDays(days - 1L);

        OffsetDateTime fromTs = start(range.from());
        OffsetDateTime toTs = start(range.to().plusDays(1));
        OffsetDateTime previousFromTs = start(previousFrom);
        OffsetDateTime previousToTs = start(range.from());

        List<RevenueRow> revenue = repository.revenueRows(fromTs, toTs);
        List<PlacedRow> placed = repository.placedRows(fromTs, toTs);
        List<RevenueRow> previousRevenue = repository.revenueRows(previousFromTs, previousToTs);
        List<PlacedRow> previousPlaced = repository.placedRows(previousFromTs, previousToTs);

        Map<String, long[]> byDay = new HashMap<>();
        Map<String, Long> byMethod = new LinkedHashMap<>();
        METHODS.forEach(method -> byMethod.put(method, 0L));
        for (RevenueRow row : revenue) {
            long[] cell = byDay.computeIfAbsent(row.getDay(), key -> new long[3]);
            cell[0] += value(row.getRevenueVnd());
            cell[1] += value(row.getPaidOrders());
            byMethod.merge(row.getPaymentMethod(), value(row.getRevenueVnd()), Long::sum);
        }
        for (PlacedRow row : placed) {
            byDay.computeIfAbsent(row.getDay(), key -> new long[3])[2] += value(row.getPlaced());
        }
        List<DayPoint> points = new ArrayList<>(days);
        for (int index = 0; index < days; index++) {
            LocalDate date = range.from().plusDays(index);
            long[] cell = byDay.getOrDefault(date.toString(), new long[3]);
            points.add(new DayPoint(date, cell[0], cell[1], cell[2]));
        }

        List<StatusCount> statuses = repository.statusRows(fromTs, toTs).stream()
                .map(row -> new StatusCount(row.getStatus(), value(row.getCount()))).toList();
        List<MethodRevenue> methods = byMethod.entrySet().stream()
                .map(entry -> new MethodRevenue(entry.getKey(), entry.getValue())).toList();
        List<TopProduct> top = repository.topProducts(fromTs, toTs).stream()
                .map(row -> new TopProduct(row.getProductId(), row.getName(), row.getImageUrl(),
                        value(row.getQuantity()), value(row.getRevenueVnd()))).toList();
        List<RecentOrder> recent = repository.recentOrders().stream()
                .map(row -> new RecentOrder(row.getOrderId(), row.getOrderNumber(), row.getCustomerName(),
                        value(row.getTotalVnd()), row.getOrderStatus(), row.getConfirmationState(),
                        Boolean.TRUE.equals(row.getPaid()), row.getPlacedAt())).toList();
        Pending pending = new Pending(repository.countAwaitingConfirmation(),
                repository.countPendingCancellationRequests(), repository.countLowStockVariants(LOW_STOCK_THRESHOLD));

        return new DashboardResponse("Asia/Ho_Chi_Minh", range.from(), range.to(), days, previousFrom, previousTo,
                OffsetDateTime.now(SHOP_ZONE), new Kpis(kpi(revenue, placed), kpi(previousRevenue, previousPlaced)),
                points, statuses, methods, top, pending, recent);
    }

    private static Kpi kpi(List<RevenueRow> revenue, List<PlacedRow> placed) {
        long total = 0;
        long paid = 0;
        for (RevenueRow row : revenue) {
            total += value(row.getRevenueVnd());
            paid += value(row.getPaidOrders());
        }
        long placedCount = placed.stream().mapToLong(row -> value(row.getPlaced())).sum();
        return new Kpi(total, placedCount, paid, paid == 0 ? 0 : Math.round((double) total / paid));
    }

    private static OffsetDateTime start(LocalDate date) {
        return date.atStartOfDay(SHOP_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static long value(Long number) {
        return number == null ? 0 : number;
    }
}
