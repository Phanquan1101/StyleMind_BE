package com.stylemind.commerce.service.revenue.impl;

import com.stylemind.commerce.dto.revenue.RevenueReportResponse;
import com.stylemind.commerce.dto.revenue.RevenueReportResponse.PeriodRow;
import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.Group;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * CSV of the period table. UTF-8 with a BOM so Excel reads Vietnamese correctly, CRLF line ends, and a leading
 * {@code sep=,} line so Excel splits columns with a comma even where the system list separator is ";" (vi-VN).
 * Money is written as plain integers (no thousand separators) so no locale can misread it.
 */
public final class RevenueCsvWriter {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");
    static final String HEADER = "Kỳ,Từ ngày,Đến ngày,Số đơn đã thanh toán,Doanh thu (VND),Hoàn tiền (VND),Thực thu (VND)";

    private RevenueCsvWriter() {
    }

    /** Period label shown in the table and in the CSV. */
    public static String label(LocalDate from, LocalDate to, Group group) {
        return switch (group) {
            case DAY -> DAY.format(from);
            case WEEK -> DAY.format(from) + " – " + DAY.format(to);
            case MONTH -> "Tháng " + MONTH.format(from);
        };
    }

    public static byte[] write(RevenueReportResponse report, Group group) {
        StringBuilder out = new StringBuilder("﻿");
        out.append("sep=,\r\n").append(HEADER).append("\r\n");
        for (PeriodRow row : report.periods()) {
            line(out, label(row.from(), row.to(), group), row.from(), row.to(), row.current());
        }
        line(out, "Tổng", report.from(), report.to(), report.totals().current());
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void line(StringBuilder out, String label, LocalDate from, LocalDate to,
            RevenueReportResponse.Metrics metrics) {
        out.append(escape(label)).append(',').append(DAY.format(from)).append(',').append(DAY.format(to)).append(',')
                .append(metrics.paidOrderCount()).append(',').append(metrics.revenueVnd()).append(',')
                .append(metrics.refundedVnd()).append(',').append(metrics.netRevenueVnd()).append("\r\n");
    }

    static String escape(String value) {
        // Neutralise spreadsheet formulas; labels here are generated, but keep the rule for any future free text.
        String safe = !value.isEmpty() && "=+@".indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        return safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")
                ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

}
