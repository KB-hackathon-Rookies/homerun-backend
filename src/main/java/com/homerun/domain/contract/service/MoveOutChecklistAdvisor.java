package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.response.MoveOutChecklistResponse;
import com.homerun.domain.contract.dto.response.MoveOutChecklistResponse.Item;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 퇴거 체크리스트를 만든다(FR-H10-04). 판정이 아니라 정해진 목록이다. 매물 유형·반환보증 가입
 * 여부로 해당 없는 항목은 applicable=false 로 구분한다 -- 근거 없이 다 해당한다고 하지 않는다.
 */
@Component
class MoveOutChecklistAdvisor {

    public MoveOutChecklistResponse guide(boolean aptOrOfficetel, boolean hasReturnGuarantee) {
        return new MoveOutChecklistResponse(List.of(
                new Item("RETURN_GUARANTEE_CANCEL", "반환보증 해지", "가입한 반환보증을 해지하면 보증료 일부를 환급받아요.", hasReturnGuarantee),
                new Item(
                        "LONG_TERM_REPAIR_RESERVE",
                        "장기수선충당금 정산",
                        "아파트·오피스텔은 그동안 낸 장기수선충당금을 임대인에게 청구해 돌려받아요.",
                        aptOrOfficetel),
                new Item("MAINTENANCE_FEE_SETTLE", "관리비 정산", "퇴거일 기준으로 관리비를 정산해요.", true),
                new Item("YEAR_END_TAX_DOCS", "연말정산 서류 챙기기", "소득공제용 서류를 미리 챙겨 둬요.", true),
                new Item("AUTOPAY_CANCEL", "자동이체 해지", "관리비·공과금 자동이체를 해지해요.", true)));
    }
}
