package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.ContractCancellationResponse;
import com.homerun.domain.contract.type.CancelFeasibility;
import com.homerun.domain.contract.type.PaymentStage;
import com.homerun.domain.property.type.RejectionCategory;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 대출 거절 후 계약 해제 대응을 안내한다(FR-P8-03·04·05). 판정이 아니라 정해진 안내다.
 *
 * <p>해제 가능성은 계약 진행 단계로 갈린다(FR-P8-04). 특약이 있으면 발동 절차를(FR-P8-03), 없으면
 * 잔금일까지 남은 일수로 대안을 분기한다(FR-P8-05). 잔여일 기준(D-25/14/7)은 은행·상품마다 다른
 * 권장치라 법정 기한처럼 다루지 않는다.
 */
@Component
class ContractCancellationAdvisor {

    private static final List<String> SPECIAL_TERM_STEPS = List.of(
            "① 은행에서 대출 거절 확인서(불승인 통지서)를 받는다.",
            "② 중개사에게 대출 미승인 사실을 알린다.",
            "③ 임대인에게 서면(내용증명)으로 계약 무효를 통지한다.",
            "④ 계약금을 반환받는다.");

    public ContractCancellationResponse guide(
            PaymentStage stage, boolean hasSpecialTerm, RejectionCategory category, Integer daysToBalance) {
        CancelFeasibility feasibility = feasibility(stage);
        String note = feasibilityNote(stage);

        if (hasSpecialTerm) {
            return new ContractCancellationResponse(
                    feasibility, note, specialTermApplicable(category), SPECIAL_TERM_STEPS, List.of());
        }
        return new ContractCancellationResponse(feasibility, note, null, List.of(), remainingDayOptions(daysToBalance));
    }

    private CancelFeasibility feasibility(PaymentStage stage) {
        return switch (stage) {
            case DOWN_PAYMENT -> CancelFeasibility.REFUNDABLE_BY_PENALTY;
            case INTERIM -> CancelFeasibility.NEEDS_COUNTERPARTY_CONSENT;
            case BALANCE -> CancelFeasibility.NOT_POSSIBLE;
        };
    }

    private String feasibilityNote(PaymentStage stage) {
        return switch (stage) {
            case DOWN_PAYMENT -> "계약금만 지급한 단계라 해약금 규정으로 해제할 수 있어요(계약금 포기 또는 배액 상환).";
            case INTERIM -> "중도금을 지급한 뒤라 임대인의 동의가 있어야 해제할 수 있어요.";
            case BALANCE -> "잔금까지 지급해 계약이 완결됐어요. 이 단계에서는 계약을 해제할 수 없어요.";
        };
    }

    /** 특약 적용 여부(FR-P8-03): 소득·신용·집 조건 거절이면 적용, 서류 미비면 임차인 과실 여지. */
    private Boolean specialTermApplicable(RejectionCategory category) {
        if (category == null) {
            return null; // 거절 사유를 모르면 적용 여부를 단정하지 않는다.
        }
        return switch (category) {
            case SUBJECT_ISSUE, PROPERTY_ISSUE, GUARANTEE_ISSUE -> true;
            case DOCUMENT_ISSUE -> false; // 서류 미비는 임차인 과실 여지가 있어 특약 적용이 어려울 수 있다.
            case LANDLORD_ISSUE -> true;
        };
    }

    /** 특약이 없을 때 잔금일까지 남은 일수로 대안을 분기한다(FR-P8-05). 전부 권장치다. */
    private List<String> remainingDayOptions(Integer daysToBalance) {
        if (daysToBalance == null) {
            return List.of("잔금일까지 남은 기간을 확인하면 대안을 알려드릴 수 있어요.");
        }
        if (daysToBalance >= 25) {
            return List.of("(권장) 다른 은행에 다시 신청해 보세요. 심사에 보통 2~3주가 걸려요.");
        }
        if (daysToBalance >= 7) {
            return List.of("(권장) 보증기관을 바꾸거나 보증금을 낮춰 협상하세요.", "(권장) 임대인에게 잔금일 연기를 요청하세요.");
        }
        return List.of("(권장) 잔금일이 얼마 남지 않았어요. 임대인에게 잔금일 연기를 요청하고, 어려우면 손실을 감수하는 것도 검토하세요.");
    }
}
