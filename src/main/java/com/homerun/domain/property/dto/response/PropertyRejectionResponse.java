package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.RejectionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 한 매물의 거절 대응 종합(FR-P8-01·02). 이 매물에서 나온 거절 사유별 대안과, 같은 사유가
 * 반복됐는지, 매물을 바꾸는 게 나은지를 함께 준다.
 *
 * @param propertyId 매물 id
 * @param guidances 이 매물에서 나온 거절 사유 분류별 대안(중복 분류는 하나로 묶음)
 * @param repeatedCategories 두 번 이상 나온 사유 분류
 * @param suggestChangeProperty 매물 변경을 권하는가(같은 사유 반복 등)
 */
@Schema(description = "매물별 거절 대응 종합(FR-P8-01·02)")
public record PropertyRejectionResponse(
        Long propertyId,
        List<RejectionGuidanceResponse> guidances,
        List<RejectionCategory> repeatedCategories,
        boolean suggestChangeProperty) {

    public PropertyRejectionResponse {
        guidances = List.copyOf(guidances);
        repeatedCategories = List.copyOf(repeatedCategories);
    }
}
