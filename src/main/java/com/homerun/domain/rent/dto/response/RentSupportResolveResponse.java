package com.homerun.domain.rent.dto.response;

import com.homerun.domain.rent.model.RentSupportCombination;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 상호배타를 푼 결과.
 *
 * @param combinations 동시 수급 가능한 조합. 총액 내림차순이고 첫 항목이 추천이다
 */
@Schema(description = "동시 수급 가능한 지원금 조합 목록")
public record RentSupportResolveResponse(List<RentSupportCombination> combinations) {

    public RentSupportResolveResponse {
        combinations = List.copyOf(combinations);
    }
}
