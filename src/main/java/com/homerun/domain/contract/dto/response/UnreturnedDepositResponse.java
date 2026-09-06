package com.homerun.domain.contract.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 보증금 미반환 대응 안내(FR-HX-02·03·BR-33).
 *
 * <p>순서를 어기면 대항력·우선변제권을 잃어 회복할 수 없다. 그래서 전입신고 유지 경고를 가장
 * 앞에 두고, 단계 건너뛰기를 막는다.
 *
 * @param topWarning 최우선 경고(전입신고 유지)
 * @param hasReturnGuarantee 반환보증 가입 여부
 * @param steps 단계별 대응 순서(가입 여부로 분기)
 * @param counselingContacts 상담 창구
 * @param doNotSkipNote 단계 건너뛰기 금지 안내
 */
@Schema(description = "보증금 미반환 대응 안내(BR-33)")
public record UnreturnedDepositResponse(
        String topWarning,
        boolean hasReturnGuarantee,
        List<String> steps,
        List<String> counselingContacts,
        String doNotSkipNote) {

    public UnreturnedDepositResponse {
        steps = List.copyOf(steps);
        counselingContacts = List.copyOf(counselingContacts);
    }
}
