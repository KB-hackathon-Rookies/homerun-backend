package com.homerun.domain.settlement.dto.response;

import com.homerun.domain.settlement.entity.FixedExpense;
import com.homerun.domain.settlement.type.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "고정지출 한 건")
public record FixedExpenseResponse(
        Long id, String name, ExpenseCategory category, long amount, Integer dueDay, boolean autopay) {

    public static FixedExpenseResponse from(FixedExpense e) {
        return new FixedExpenseResponse(
                e.getId(), e.getName(), e.getCategory(), e.getAmount(), e.getDueDay(), e.isAutopay());
    }
}
