package com.homerun.domain.settlement.dto.request;

import com.homerun.domain.settlement.type.ExpenseCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 고정지출 등록 입력(FR-H5-01).
 *
 * @param name 항목 이름
 * @param category 종류(이자/관리비/기타)
 * @param amount 월 금액(원)
 * @param dueDay 납부일(1~31). 없으면 null
 * @param autopay 자동이체 등록 여부(FR-H5-02)
 */
@Schema(description = "고정지출 등록(FR-H5-01)")
public record FixedExpenseRequest(
        @NotBlank String name,
        @NotNull ExpenseCategory category,
        @NotNull @PositiveOrZero Long amount,
        @Min(1) @Max(31) Integer dueDay,
        boolean autopay) {}
