package com.homerun.domain.asset.service;

import com.homerun.domain.asset.dto.request.AssetComparisonRequest;
import com.homerun.domain.asset.dto.response.AssetComparisonResponse;
import com.homerun.domain.asset.dto.response.AssetComparisonResult;
import com.homerun.domain.asset.entity.AssetOption;
import com.homerun.domain.asset.repository.AssetOptionRepository;
import com.homerun.domain.asset.type.AssetType;
import com.homerun.domain.fact.exception.FactNotFoundException;
import com.homerun.domain.fact.exception.UnusableFactException;
import com.homerun.domain.fact.model.Fact;
import com.homerun.domain.fact.service.FactRegistry;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 노후자산(IRP·주택청약·청년적금)을 깨서 보증금을 만드는 선택을 금액으로 비교한다(AST-01).
 *
 * <p>IRP는 세율이 팩트로 확정돼 있어(FCT-078) 세금·실수령액까지 계산한다. 주택청약·청년적금은
 * 손실액을 계산하려면 과거 납입액·가입연수가 있어야 하는데 어디서도 그 값을 안 걷고 있어서, 세금 관련
 * 컬럼은 비우고 사실 문장(FCT-080~082, FCT-083~084)만 안내한다 — 없는 값을 0으로 채우면 "손해가 없다"는
 * 잘못된 안내가 된다.
 *
 * <p>대출 이자 비교의 기준 금리는 청년버팀목(FCT-172/173)을 쓴다. 레지스트리에 '일반 전세대출
 * 금리' 같은 범용 팩트가 따로 없어서, 이미 시스템에 있는 대표 상품 금리를 그대로 재사용했다.
 */
@Service
public class AssetComparisonService {

    private final PlanRepository planRepository;
    private final AssetOptionRepository assetOptionRepository;
    private final FactRegistry facts;

    public AssetComparisonService(
            PlanRepository planRepository, AssetOptionRepository assetOptionRepository, FactRegistry facts) {
        this.planRepository = planRepository;
        this.assetOptionRepository = assetOptionRepository;
        this.facts = facts;
    }

    @Transactional
    public AssetComparisonResponse compare(Long memberId, Long planId, AssetComparisonRequest request) {
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND));
        plan.verifyOwner(memberId);

        List<AssetComparisonResult> results = request.assets().stream()
                .map(entry -> compareOne(planId, entry))
                .toList();

        return new AssetComparisonResponse(planId, results);
    }

    private AssetComparisonResult compareOne(Long planId, AssetComparisonRequest.Entry entry) {
        AssetComparisonResult result =
                switch (entry.assetType()) {
                    case IRP -> compareIrp(entry.withdrawAmount());
                    case HOUSING_SUBSCRIPTION -> compareHousingSubscription(entry.withdrawAmount());
                    case YOUTH_SAVINGS -> compareYouthSavings(entry.withdrawAmount());
                };

        assetOptionRepository.save(AssetOption.create(
                planId,
                entry.assetType(),
                entry.balance(),
                entry.withdrawAmount(),
                result.taxPenaltyRate(),
                result.taxPenaltyAmount(),
                result.comparedLoanInterestMin()));

        return result;
    }

    private AssetComparisonResult compareIrp(Long withdrawAmount) {
        Optional<Fact> taxFact = resolveFact("FCT-078");
        BigDecimal taxRate = taxFact.map(Fact::requireNumber).orElse(null);
        Long taxAmount = taxRate == null ? null : won(withdrawAmount, taxRate);
        Long netAmount = taxAmount == null ? null : withdrawAmount - taxAmount;

        List<String> notes = new ArrayList<>();
        notes.add("전세보증금 마련은 IRP의 '부득이한 인출 사유'에 해당하지 않아 저율과세를 받지 못합니다(FCT-079).");

        return new AssetComparisonResult(
                AssetType.IRP,
                withdrawAmount,
                taxRate,
                taxAmount,
                netAmount,
                comparedLoanInterest(withdrawAmount, "FCT-172"),
                comparedLoanInterest(withdrawAmount, "FCT-173"),
                false,
                notes);
    }

    private AssetComparisonResult compareHousingSubscription(Long withdrawAmount) {
        List<String> notes = new ArrayList<>();
        resolveFact("FCT-080").map(Fact::text).ifPresent(text -> notes.add("5년 이내 해지: " + text + "."));
        resolveFact("FCT-081")
                .map(Fact::text)
                .ifPresent(text -> notes.add("추징 면제 조건: " + text + " — 전세보증금 마련은 여기 해당하지 않습니다."));
        resolveFact("FCT-082").map(Fact::text).ifPresent(text -> notes.add("소득공제 한도: " + text + "."));
        notes.add("정확한 추징액은 그동안의 납입액·가입기간에 따라 달라 여기서 계산하지 않습니다. 국세청 안내를 확인하세요.");

        return new AssetComparisonResult(
                AssetType.HOUSING_SUBSCRIPTION,
                withdrawAmount,
                null,
                null,
                null,
                comparedLoanInterest(withdrawAmount, "FCT-172"),
                comparedLoanInterest(withdrawAmount, "FCT-173"),
                false,
                notes);
    }

    /**
     * AST-01-06. 청년미래적금 일반 중도해지 시 정부기여금·비과세를 잃는다(FCT-083). 손실액은
     * 그동안 몇 개월 얼마씩 냈는지에 달려 있는데 그 이력을 이 서비스가 안 걷어서, 주택청약과
     * 같은 이유로 금액을 계산하지 않고 사실 문장만 안내한다.
     */
    private AssetComparisonResult compareYouthSavings(Long withdrawAmount) {
        List<String> notes = new ArrayList<>();
        resolveFact("FCT-083").map(Fact::text).ifPresent(text -> notes.add("일반 중도해지: " + text + "."));
        resolveFact("FCT-084")
                .map(Fact::text)
                .ifPresent(text -> notes.add("특별중도해지 유지 조건: " + text + " — 전세보증금 마련은 여기 해당하지 않습니다."));
        notes.add("정부기여금 손실액은 그동안의 납입 개월수·금액에 따라 달라 여기서 계산하지 않습니다. 은행 안내를 확인하세요.");

        return new AssetComparisonResult(
                AssetType.YOUTH_SAVINGS,
                withdrawAmount,
                null,
                null,
                null,
                comparedLoanInterest(withdrawAmount, "FCT-172"),
                comparedLoanInterest(withdrawAmount, "FCT-173"),
                false,
                notes);
    }

    private Long comparedLoanInterest(Long amount, String rateFactCode) {
        return resolveFact(rateFactCode)
                .map(fact -> won(amount, fact.requireNumber()))
                .orElse(null);
    }

    /** amount 의 percent% — 세금·이자 계산 공용. */
    private Long won(Long amount, BigDecimal percent) {
        return BigDecimal.valueOf(amount)
                .multiply(percent)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private Optional<Fact> resolveFact(String factCode) {
        try {
            return Optional.of(facts.require(factCode));
        } catch (FactNotFoundException | UnusableFactException e) {
            return Optional.empty();
        }
    }
}
