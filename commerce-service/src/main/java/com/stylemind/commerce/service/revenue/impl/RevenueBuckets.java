package com.stylemind.commerce.service.revenue.impl;

import com.stylemind.commerce.validation.revenue.RevenueRequestValidator.Group;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Splits [from, to] into consecutive day / ISO-week (Mon-Sun) / calendar-month buckets, clipped to the range. */
final class RevenueBuckets {

    record Bucket(LocalDate from, LocalDate to) {
    }

    private RevenueBuckets() {
    }

    static List<Bucket> split(LocalDate from, LocalDate to, Group group) {
        List<Bucket> buckets = new ArrayList<>();
        LocalDate start = from;
        while (!start.isAfter(to)) {
            LocalDate end = switch (group) {
                case DAY -> start;
                case WEEK -> start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
                case MONTH -> start.with(TemporalAdjusters.lastDayOfMonth());
            };
            if (end.isAfter(to)) {
                end = to;
            }
            buckets.add(new Bucket(start, end));
            start = end.plusDays(1);
        }
        return buckets;
    }
}
