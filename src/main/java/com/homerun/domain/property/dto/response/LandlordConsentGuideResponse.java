package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.type.LandlordConsent;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 임대인 협조 상태별 안내(FR-P1-08).
 *
 * @param consent 현재 협조 상태
 * @param bankConsultationWarning 은행 상담 진입 전 경고. 확인됐으면 null
 * @param scripts 중개사 질문·설득 스크립트. CONFIRMED 면 비어 있다
 * @param suggestOtherProperty REFUSED 일 때 다른 매물을 권한다
 */
@Schema(description = "임대인 협조 상태별 안내(FR-P1-08)")
public record LandlordConsentGuideResponse(
        LandlordConsent consent, String bankConsultationWarning, List<String> scripts, boolean suggestOtherProperty) {

    public LandlordConsentGuideResponse {
        scripts = List.copyOf(scripts);
    }
}
