package com.homerun.domain.verification.dto.response;

import com.homerun.domain.policy.entity.VerdictBasis;
import java.util.List;

/** 추가 확인 관리(VER-01) 응답. */
public final class VerificationDtos {

    private VerificationDtos() {}

    /**
     * 추가 확인이 필요한 조건 하나(VER-01-01).
     *
     * @param requiredText 무엇을 충족해야 하는지
     * @param sourceUrl 이 조건을 확인할 수 있는 공식 절차·서류 안내 페이지(VER-01-02). 근거가 없으면
     *     null이다 — 없는 링크를 지어내지 않는다(NFR-01-06)
     */
    public record PendingCondition(
            String policyCode,
            String policyName,
            String conditionCode,
            String conditionLabel,
            String requiredText,
            String factCode,
            String sourceUrl) {

        public static PendingCondition from(String policyCode, String policyName, VerdictBasis basis) {
            return new PendingCondition(
                    policyCode,
                    policyName,
                    basis.getConditionCode(),
                    basis.getConditionLabel(),
                    basis.getRequiredText(),
                    basis.getFactCode(),
                    basis.getSourceUrl());
        }
    }

    /** 계획 전체의 미확인 조건 목록. */
    public record PendingConditionList(List<PendingCondition> conditions) {

        public PendingConditionList {
            conditions = List.copyOf(conditions);
        }
    }
}
