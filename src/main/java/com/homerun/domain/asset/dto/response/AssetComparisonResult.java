package com.homerun.domain.asset.dto.response;

import com.homerun.domain.asset.type.AssetType;
import java.math.BigDecimal;
import java.util.List;

/**
 * @param taxPenaltyRate IRP만 계산된다(FCT-078, 16.5%). 주택청약은 추징액을 계산할 근거(과거
 *     납입액·가입연수)가 없어 null — 억지로 채우지 않는다
 * @param taxPenaltyAmount withdrawAmount × taxPenaltyRate. 주택청약은 null
 * @param netAmount 세금을 뗀 실수령액. taxPenaltyAmount 를 모르면 이것도 null 이다 — 세금이
 *     0원이라고 단정하는 게 아니라 모른다는 뜻이다
 * @param comparedLoanInterestMin/Max 같은 금액을 대출로 조달했다면 드는 연이자. 기준 금리는
 *     청년버팀목(FCT-172/173) — 레지스트리에 '일반 전세대출 금리' 팩트가 따로 없어서 이걸 썼다
 * @param isReversible 항상 false. 인출·해지는 되돌릴 수 없다
 * @param notes 세율로 못 채운 사실을 문장으로 보여준다(청약저축 추징·순위초기화 등)
 */
public record AssetComparisonResult(
        AssetType assetType,
        Long withdrawAmount,
        BigDecimal taxPenaltyRate,
        Long taxPenaltyAmount,
        Long netAmount,
        Long comparedLoanInterestMin,
        Long comparedLoanInterestMax,
        boolean isReversible,
        List<String> notes) {}
