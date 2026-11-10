package com.stylemind.stylist.service.recommendation.internal;

import com.stylemind.stylist.model.recommendation.EngineModel.Item;
import java.math.BigDecimal;
import java.util.List;

public final class BudgetPolicy {
    private BudgetPolicy() { }

    public static long exactVnd(BigDecimal value) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException("Invalid VND");
        try { return value.longValueExact(); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("VND must be an exact nonnegative long", ex); }
    }

    public static long total(List<Item> items) {
        long total = 0;
        for (Item item : items) total = Math.addExact(total, item.additionalCostVnd());
        return total;
    }
}
