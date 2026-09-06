package com.homerun.domain.settlement.service;

import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.settlement.dto.response.ReturnGuaranteeGuideResponse;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 반환보증 가입 안내를 담보별로 분기한다(FR-H1-01·BR-32). 판정이 아니라 정해진 안내라 담보만 받는다.
 *
 * <p>핵심은 HUG 담보 사용자에게 반환보증 가입을 다시 노출하지 않는 것이다 -- 대출보증에 이미
 * 포함돼 있어 노출하면 잘못된 안내가 된다.
 */
@Component
class ReturnGuaranteeAdvisor {

    private static final String TIMING = "전입신고·확정일자를 마친 뒤 즉시 가입하세요.";
    private static final List<String> CHANNELS = List.of("안심전세App·네이버부동산·카카오페이·토스", "HUG 지사·위탁은행", "HUG 콜센터 1566-9009");
    private static final List<String> DOCUMENTS = List.of("계약서 사본", "주민등록등본", "등기부등본", "전입세대확인서");
    private static final String REUSE_NOTE = "3루에서 뗀 서류가 3개월 이내면 다시 발급하지 않고 재사용할 수 있어요(BR-26).";

    public ReturnGuaranteeGuideResponse guide(CollateralMethod guarantee) {
        if (guarantee == null || guarantee == CollateralMethod.UNKNOWN) {
            return new ReturnGuaranteeGuideResponse(
                    false, "담보를 확인해야 반환보증 안내를 드릴 수 있어요.", null, List.of(), List.of(), null);
        }
        if (guarantee == CollateralMethod.HUG_SAFE_JEONSE) {
            return new ReturnGuaranteeGuideResponse(
                    false, "HUG 안심전세는 반환보증이 이미 포함돼 있어요. 따로 가입하지 않아도 돼요.", null, List.of(), List.of(), null);
        }
        // HF·SGI·채권양도·그 외 -- 반환보증을 따로 가입해야 한다.
        return new ReturnGuaranteeGuideResponse(true, "반환보증을 따로 가입해야 해요.", TIMING, CHANNELS, DOCUMENTS, REUSE_NOTE);
    }
}
