package com.homerun.domain.property.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 온라인 매물 탐색 안내(FR-P0). 2루 진입 직전, 매물 사이트에서 무엇을 필터로 넣고 무엇을
 * 걸러야 하는지 안내한다. 판정이 아니라 안내라 계획을 고치지 않는다.
 *
 * @param filter 1루 조건을 매물 사이트 필터 형태로 정리한 것(FR-P0-01)
 * @param sites 탐색처 링크(FR-P0-02)
 * @param siteHint 같은 매물이 여러 사이트에 있으면 정상, 한 곳에만 있으면 의심하라는 힌트
 * @param warnings 목록에서 미리 거를 4가지(FR-P0-03)
 */
@Schema(description = "온라인 매물 탐색 안내(FR-P0)")
public record PropertySearchGuideResponse(Filter filter, List<Site> sites, String siteHint, List<String> warnings) {

    public PropertySearchGuideResponse {
        sites = List.copyOf(sites);
        warnings = List.copyOf(warnings);
    }

    /**
     * 매물 사이트에 넣을 필터. 사이트마다 URL 파라미터가 달라 값만 정리해 주고 입력은 사용자가 한다.
     *
     * @param depositCeiling 보증금 상한(원). 계획에 없으면 null
     * @param areaCeilingM2 전용면적 상한(㎡). config_effective 에서 읽는다
     * @param regionName 희망 지역 이름. 계획에 없으면 null
     * @param houseTypes 대상 주택유형
     */
    public record Filter(
            Long depositCeiling, java.math.BigDecimal areaCeilingM2, String regionName, List<String> houseTypes) {
        public Filter {
            houseTypes = List.copyOf(houseTypes);
        }
    }

    public record Site(String name, String url) {}
}
