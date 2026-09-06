package com.homerun.domain.policy.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 부대비용 내역(BR-08a). "실제로 필요한 돈"에 더해 계약·입주에 드는 한 번 비용이다.
 *
 * <p>계약금(보증금의 5~10%)은 여기 넣지 않는다 — 부대비용이 아니라 잔금의 선지급분이라 별도로
 * 안내한다(FR-D5-02). 보증료는 담보가 확정되기 전이면 중간값 추정이라 {@code estimated} 가 참이다.
 *
 * @param brokerageFee 중개보수(부가세 포함, BR-27). 6억 초과 등 요율 미확보면 null
 * @param stampDuty 인지세 고객 부담분(대출금 구간)
 * @param guaranteeFee 대출보증 보증료(대출금 × 보증료율)
 * @param movingCost 이사비(사용자 입력 또는 기본값)
 * @param documentFee 서류 발급비
 * @param total 위 항목 합계. 산출 못 한 항목이 있으면 null
 * @param estimated 보증료율이 추정치(담보 미확정)인가
 */
@Schema(description = "부대비용 내역(BR-08a)")
public record AncillaryCostResponse(
        Long brokerageFee,
        long stampDuty,
        long guaranteeFee,
        long movingCost,
        long documentFee,
        Long total,
        boolean estimated) {}
