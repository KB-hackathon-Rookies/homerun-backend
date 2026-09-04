package com.homerun.domain.policy.dto.response;

import java.math.BigDecimal;

/** 새로 우대금리 대상이 된 조건 하나(POL-01-06). rateBonus 단위는 %p — 기준금리에서 이만큼 뺀다. */
public record PreferentialRateChange(
        String code, String label, BigDecimal rateBonus, String requiredText, String sourceUrl) {}
