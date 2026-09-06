package com.homerun.domain.contract.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 퇴거 체크리스트(FR-H10-04). 매물 유형·반환보증 가입 여부로 해당 항목이 갈린다.
 *
 * @param items 체크 항목. applicable=false 는 이 계약에 해당하지 않는 항목이다
 */
@Schema(description = "퇴거 체크리스트(FR-H10-04)")
public record MoveOutChecklistResponse(List<Item> items) {

    public MoveOutChecklistResponse {
        items = List.copyOf(items);
    }

    /**
     * @param code 항목 코드
     * @param label 항목 이름
     * @param note 유의사항
     * @param applicable 이 계약에 해당하는가
     */
    public record Item(String code, String label, String note, boolean applicable) {}
}
