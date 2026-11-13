package com.stylemind.commerce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.stylemind.commerce.dto.revenue.RevenueReportResponse;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.PeriodRow;
import com.stylemind.commerce.errors.ApiOperationException;
import com.stylemind.commerce.repository.revenue.RevenueRepository;
import com.stylemind.commerce.repository.revenue.RevenueRepository.DailyRow;
import com.stylemind.commerce.service.revenue.impl.RevenueCsvWriter;
import com.stylemind.commerce.service.revenue.impl.RevenueServiceImpl;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.Group;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.RevenueQuery;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RevenueReportTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private final RevenueRequestValidator validator = new RevenueRequestValidator();

    private static DailyRow row(String day, String method, long paid, long refund, long orders) {
        DailyRow row = mock(DailyRow.class);
        when(row.getDay()).thenReturn(day);
        when(row.getPaymentMethod()).thenReturn(method);
        when(row.getPaidVnd()).thenReturn(paid);
        when(row.getRefundVnd()).thenReturn(refund);
        when(row.getPaidOrders()).thenReturn(orders);
        return row;
    }

    /** Current range 2026-11-01..2026-11-20 (20 days); previous range is 2026-10-12..2026-10-31. */
    private RevenueServiceImpl service() {
        RevenueRepository repository = mock(RevenueRepository.class);
        when(repository.dailyRows(any(OffsetDateTime.class), any(OffsetDateTime.class))).thenAnswer(invocation -> {
            OffsetDateTime from = invocation.getArgument(0);
            // The previous-range call starts before November (shop time is UTC+7, so compare by the UTC instant).
            return from.isBefore(OffsetDateTime.parse("2026-10-31T17:00:00Z")) ? List.of(
                    row("2026-10-20", "COD", 500_000, 0, 1),
                    row("2026-10-30", "VNPAY", 1_000_000, 200_000, 2)) : List.of(
                    row("2026-11-01", "VNPAY", 1_000_000, 0, 1),
                    row("2026-11-03", "COD", 2_000_000, 0, 2),
                    row("2026-11-03", "SEPAY_VIETQR", 300_000, 100_000, 1),
                    row("2026-11-09", "VNPAY", 700_000, 0, 1),
                    row("2026-11-15", "COD", 0, 400_000, 0),
                    row("2026-11-20", "COD", 900_000, 0, 1));
        });
        when(repository.categoryRows(any(), any())).thenReturn(List.of());
        when(repository.topProducts(any(), any(), anyInt())).thenReturn(List.of());
        return new RevenueServiceImpl(repository);
    }

    private RevenueReportResponse report(Group group) {
        return service().report(new RevenueQuery(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 20), group));
    }

    @Test
    void totalsAreTheSameForEveryGrouping() {
        var day = report(Group.DAY);
        var week = report(Group.WEEK);
        var month = report(Group.MONTH);
        assertThat(day.totals()).isEqualTo(week.totals()).isEqualTo(month.totals());
        assertThat(day.totals().current().revenueVnd()).isEqualTo(4_900_000);
        assertThat(day.totals().current().refundedVnd()).isEqualTo(500_000);
        assertThat(day.totals().current().netRevenueVnd()).isEqualTo(4_400_000);
        assertThat(day.totals().current().paidOrderCount()).isEqualTo(6);
        assertThat(day.totals().previous().netRevenueVnd()).isEqualTo(1_300_000);
        for (var report : List.of(day, week, month)) {
            assertThat(report.periods().stream().mapToLong(period -> period.current().netRevenueVnd()).sum()).isEqualTo(4_400_000);
            assertThat(report.periods().stream().mapToLong(period -> period.current().paidOrderCount()).sum()).isEqualTo(6);
            assertThat(report.periods().stream().mapToLong(period -> period.previous().netRevenueVnd()).sum()).isEqualTo(1_300_000);
        }
    }

    @Test
    void bucketsPartitionTheRangeWithoutGapsOrOverlap() {
        assertThat(report(Group.DAY).periods()).hasSize(20);
        // 2026-11-01 is a Sunday: weeks are 1 | 2-8 | 9-15 | 16-20 (clipped at the end of the range).
        var weeks = report(Group.WEEK).periods();
        assertThat(weeks).extracting(PeriodRow::from).containsExactly(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 16));
        assertThat(weeks).extracting(PeriodRow::to).containsExactly(
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 20));
        var months = report(Group.MONTH).periods();
        assertThat(months).hasSize(1);
        assertThat(months.get(0).from()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(months.get(0).to()).isEqualTo(LocalDate.of(2026, 11, 20));
    }

    @Test
    void monthGroupingSplitsAtMonthEndsAndEmptyPeriodsAreZero() {
        RevenueServiceImpl service = service();
        var report = service.report(new RevenueQuery(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 5), Group.MONTH));
        assertThat(report.periods()).extracting(PeriodRow::from).containsExactly(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 1));
        assertThat(report.periods()).extracting(PeriodRow::to).containsExactly(LocalDate.of(2026, 10, 31), LocalDate.of(2026, 11, 5));
        var empty = report(Group.DAY).periods().get(1); // 2026-11-02 has no activity
        assertThat(empty.current().revenueVnd()).isZero();
        assertThat(empty.current().paidOrderCount()).isZero();
    }

    @Test
    void previousPeriodIsShiftedByTheRangeLengthAndLinesUpByIndex() {
        var report = report(Group.DAY);
        assertThat(report.previousFrom()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(report.previousTo()).isEqualTo(LocalDate.of(2026, 10, 31));
        PeriodRow first = report.periods().get(0);
        assertThat(first.previousFrom()).isEqualTo(LocalDate.of(2026, 10, 12));
        PeriodRow last = report.periods().get(19);
        assertThat(last.previousTo()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(report.periods().get(18).previous().revenueVnd()).isEqualTo(1_000_000); // 2026-11-19 shifts to 2026-10-30
        assertThat(report.periods().get(8).previous().revenueVnd()).isEqualTo(500_000); // 2026-10-20 is index 8
    }

    @Test
    void methodsAlwaysListedWithNetEqualToCollectedMinusRefunded() {
        var methods = report(Group.DAY).byPaymentMethod();
        assertThat(methods).extracting(m -> m.paymentMethod()).containsExactly("VNPAY", "COD", "SEPAY_VIETQR");
        assertThat(methods.get(0).revenueVnd()).isEqualTo(1_700_000);
        assertThat(methods.get(1).refundedVnd()).isEqualTo(400_000);
        assertThat(methods.get(1).netRevenueVnd()).isEqualTo(2_500_000);
        assertThat(methods.stream().mapToLong(m -> m.netRevenueVnd()).sum()).isEqualTo(4_400_000);
    }

    @Test
    void csvHasBomSepLineVietnameseHeaderAndMatchesTheTable() {
        var week = report(Group.WEEK);
        byte[] bytes = RevenueCsvWriter.write(week, Group.WEEK);
        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.substring(1).split("\r\n");
        assertThat(lines[0]).isEqualTo("sep=,");
        assertThat(lines[1]).isEqualTo("Kỳ,Từ ngày,Đến ngày,Số đơn đã thanh toán,Doanh thu (VND),Hoàn tiền (VND),Thực thu (VND)");
        assertThat(lines).hasSize(2 + week.periods().size() + 1);
        assertThat(lines[3]).isEqualTo("02/11/2026 – 08/11/2026,02/11/2026,08/11/2026,3,2300000,100000,2200000");
        assertThat(lines[lines.length - 1]).isEqualTo("Tổng,01/11/2026,20/11/2026,6,4900000,500000,4400000");
        assertThat(RevenueCsvWriter.label(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), Group.MONTH)).isEqualTo("Tháng 11/2026");
        assertThat(RevenueCsvWriter.label(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 13), Group.DAY)).isEqualTo("13/11/2026");
    }

    @Test
    void validatorAcceptsDefaultsAndGroupsAndRejectsBadInputByField() {
        assertThat(validator.query(new MockHttpServletRequest(), TODAY).days()).isEqualTo(30);
        assertThat(validator.query(new MockHttpServletRequest(), TODAY).group()).isEqualTo(Group.DAY);
        MockHttpServletRequest weekly = new MockHttpServletRequest();
        weekly.addParameter("from", "2026-11-01");
        weekly.addParameter("to", "2026-11-13");
        weekly.addParameter("group", "week");
        assertThat(validator.query(weekly, TODAY).group()).isEqualTo(Group.WEEK);

        String[][] bad = {
            {"from", "2026-11-14", "to", "2026-11-13"},
            {"from", "2026-11-01", "to", "2026-11-14"},
            {"from", "2025-11-12", "to", "2026-11-13"},
            {"from", "2026-11-01", "to", "2026-11-13", "group", "year"},
            {"from", "2026-11-01", "to", "2026-11-13", "group", ""},
            {"from", "x", "to", "2026-11-13"},
            {"from", "2026-11-01"},
            {"bogus", "1"},
        };
        for (String[] pairs : bad) {
            MockHttpServletRequest request = new MockHttpServletRequest();
            for (int index = 0; index < pairs.length; index += 2) {
                request.addParameter(pairs[index], pairs[index + 1]);
            }
            assertThatThrownBy(() -> validator.query(request, TODAY))
                    .as(String.join(",", pairs))
                    .isInstanceOfSatisfying(ApiOperationException.class, exception -> {
                        assertThat(exception.status().value()).isEqualTo(400);
                        assertThat(exception.fieldErrors()).isNotEmpty();
                    });
        }
    }
}
