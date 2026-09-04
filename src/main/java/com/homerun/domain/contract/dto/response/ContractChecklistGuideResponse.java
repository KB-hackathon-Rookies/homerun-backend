package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.type.ContractChecklistPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "COM-12-01 계약 체크리스트 안내. 개인별 완료 상태나 안전 판정이 아닙니다.")
public record ContractChecklistGuideResponse(String version, String notice, List<Item> items) {
    public ContractChecklistGuideResponse {
        items = List.copyOf(items);
    }

    public record Item(
            @Schema(description = "안내 항목 식별자") String code,
            ContractChecklistPhase phase,
            @Schema(description = "전체 안내 순서") int sequence,
            String title,
            String action,
            String caution,

            @Schema(description = "관련 서류 코드. /api/v1/documents/{code}의 발급 안내와 연결하며 없으면 null")
            String documentCode,

            String officialSiteName,

            @Schema(description = "공식 확인·처리 경로. 로그인이나 별도 검색이 필요할 수 있습니다.")
            String officialUrl) {}
}
