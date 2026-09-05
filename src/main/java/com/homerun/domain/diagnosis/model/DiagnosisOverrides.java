package com.homerun.domain.diagnosis.model;

import java.time.LocalDate;

/**
 * 계획을 고치지 않고 진단 축만 바꿔 계산할 때 쓰는 가정값(ALT-01-02).
 *
 * <p>둘 다 null 이면 {@link #none()} 과 같고, 그때는 계획과 계획 입력에 저장된 값을 그대로 쓴다 —
 * 즉 기존 진단 계산과 결과가 같다.
 *
 * <p>지역은 여기 없다. 진단 계산은 지역을 입력으로 받지 않는다 — 지역은 정책 판정을 거쳐
 * {@code expectedLoanAmount} 로 들어온다. 여기에 지역을 넣으면 같은 값을 두 곳에서 계산하게 된다.
 */
public record DiagnosisOverrides(Long hopeDeposit, LocalDate targetMoveDate) {

    private static final DiagnosisOverrides NONE = new DiagnosisOverrides(null, null);

    public static DiagnosisOverrides none() {
        return NONE;
    }

    public boolean isEmpty() {
        return hopeDeposit == null && targetMoveDate == null;
    }

    public long hopeDepositOr(long stored) {
        return hopeDeposit == null ? stored : hopeDeposit;
    }

    public LocalDate targetMoveDateOr(LocalDate stored) {
        return targetMoveDate == null ? stored : targetMoveDate;
    }
}
