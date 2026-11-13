package com.stylemind.commerce.service.revenue.impl;

import com.stylemind.commerce.dto.revenue.RevenueReportResponse;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.CategoryRevenue;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.MethodRevenue;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.Metrics;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.PeriodRow;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.Totals;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.TopProduct;
import com.stylemind.commerce.repository.revenue.RevenueRepository;
import com.stylemind.commerce.repository.revenue.RevenueRepository.DailyRow;
import com.stylemind.commerce.service.revenue.RevenueService;
import com.stylemind.commerce.service.revenue.impl.RevenueBuckets.Bucket;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.RevenueQuery;
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
public class RevenueServiceImpl implements RevenueService {

    static final ZoneId SHOP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int TOP_PRODUCTS = 10;
    /** Shown even when zero so the table is stable. */
    private static final List<String> METHODS = List.of("VNPAY", "COD", "SEPAY_VIETQR");

    private final RevenueRepository repository;

    @Override
    public LocalDate today() {
        return LocalDate.now(SHOP_ZONE);
    }

    @Override
    @Transactional(readOnly = true)
    public RevenueReportResponse report(RevenueQuery query) {
        int days = query.days();
        LocalDate previousFrom = query.from().minusDays(days);
        LocalDate previousTo = query.from().minusDays(1);
        OffsetDateTime fromTs = start(query.from());
        OffsetDateTime toTs = start(query.to().plusDays(1));

        List<DailyRow> current = repository.dailyRows(fromTs, toTs);
        List<DailyRow> previous = repository.dailyRows(start(previousFrom), start(query.from()));
        Map<String, long[]> currentByDay = byDay(current);
        Map<String, long[]> previousByDay = byDay(previous);

        List<PeriodRow> periods = new ArrayList<>();
        for (Bucket bucket : RevenueBuckets.split(query.from(), query.to(), query.group())) {
            LocalDate shiftedFrom = bucket.from().minusDays(days);
            LocalDate shiftedTo = bucket.to().minusDays(days);
            periods.add(new PeriodRow(bucket.from(), bucket.to(), shiftedFrom, shiftedTo,
                    sum(currentByDay, bucket.from(), bucket.to()), sum(previousByDay, shiftedFrom, shiftedTo)));
        }

        Map<String, long[]> methods = new LinkedHashMap<>();
        METHODS.forEach(method -> methods.put(method, new long[3]));
        for (DailyRow row : current) {
            long[] cell = methods.computeIfAbsent(row.getPaymentMethod(), key -> new long[3]);
            cell[0] += value(row.getPaidVnd());
            cell[1] += value(row.getRefundVnd());
            cell[2] += value(row.getPaidOrders());
        }
        List<MethodRevenue> byMethod = methods.entrySet().stream()
                .map(entry -> new MethodRevenue(entry.getKey(), entry.getValue()[0], entry.getValue()[1],
                        entry.getValue()[0] - entry.getValue()[1], entry.getValue()[2])).toList();
        List<CategoryRevenue> byCategory = repository.categoryRows(fromTs, toTs).stream()
                .map(row -> new CategoryRevenue(row.getCategoryId(), row.getName(), value(row.getRevenueVnd()),
                        value(row.getQuantity()))).toList();
        List<TopProduct> top = repository.topProducts(fromTs, toTs, TOP_PRODUCTS).stream()
                .map(row -> new TopProduct(row.getProductId(), row.getName(), row.getImageUrl(),
                        value(row.getQuantity()), value(row.getRevenueVnd()))).toList();

        Totals totals = new Totals(total(current), total(previous));
        return new RevenueReportResponse("Asia/Ho_Chi_Minh", query.from(), query.to(), days, previousFrom, previousTo,
                query.group().wire(), OffsetDateTime.now(SHOP_ZONE), totals, periods, byCategory, byMethod, top);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportCsv(RevenueQuery query) {
        return RevenueCsvWriter.write(report(query), query.group());
    }

    /** day -> [collected, refunded, collected orders], methods merged. */
    private static Map<String, long[]> byDay(List<DailyRow> rows) {
        Map<String, long[]> days = new HashMap<>();
        for (DailyRow row : rows) {
            long[] cell = days.computeIfAbsent(row.getDay(), key -> new long[3]);
            cell[0] += value(row.getPaidVnd());
            cell[1] += value(row.getRefundVnd());
            cell[2] += value(row.getPaidOrders());
        }
        return days;
    }

    private static Metrics sum(Map<String, long[]> byDay, LocalDate from, LocalDate to) {
        long revenue = 0;
        long refunded = 0;
        long orders = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            long[] cell = byDay.get(day.toString());
            if (cell != null) {
                revenue += cell[0];
                refunded += cell[1];
                orders += cell[2];
            }
        }
        return Metrics.of(orders, revenue, refunded);
    }

    private static Metrics total(List<DailyRow> rows) {
        long revenue = 0;
        long refunded = 0;
        long orders = 0;
        for (DailyRow row : rows) {
            revenue += value(row.getPaidVnd());
            refunded += value(row.getRefundVnd());
            orders += value(row.getPaidOrders());
        }
        return Metrics.of(orders, revenue, refunded);
    }

    private static OffsetDateTime start(LocalDate date) {
        return date.atStartOfDay(SHOP_ZONE).toOffsetDateTime().withOffsetSameInstant(ZoneOffset.UTC);
    }

    private static long value(Long number) {
        return number == null ? 0 : number;
    }
}
