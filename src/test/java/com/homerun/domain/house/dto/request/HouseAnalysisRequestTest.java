package com.homerun.domain.house.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.global.external.building.BuildingLotQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주소 검색이 준 값을 그대로 대장 조회 파라미터로 옮기는지 본다.
 *
 * <p>부번은 비워서 올 수 있다(부번 없는 지번). 이 정규화가 여러 곳에 흩어져 있으면 한쪽만
 * 고쳐져 같은 집을 다른 지번으로 조회하게 된다.
 */
class HouseAnalysisRequestTest {

    @Test
    @DisplayName("주소 검색 값을 그대로 옮긴다")
    void should_carryAddressValues_asLotQuery() {
        assertThat(request("1168010100", false, "648", "23").toLotQuery())
                .isEqualTo(new BuildingLotQuery("1168010100", false, "648", "23"));
    }

    @Test
    @DisplayName("부번이 비어 있으면 0으로 맞춘다")
    void should_defaultSubLotNumberToZero_whenItIsMissing() {
        assertThat(request("1168010100", false, "648", null).toLotQuery().subLotNumber())
                .isEqualTo("0");
        assertThat(request("1168010100", false, "648", " ").toLotQuery().subLotNumber())
                .isEqualTo("0");
    }

    @Test
    @DisplayName("산 소재지 여부를 잃지 않는다")
    void should_keepMountainFlag() {
        // 같은 번지라도 산과 대지는 다른 땅이다. 빠뜨리면 엉뚱한 건물이 조회된다.
        assertThat(request("1168010100", true, "12", "3").toLotQuery().platGbCode())
                .isEqualTo("1");
    }

    private HouseAnalysisRequest request(String code, boolean mountain, String main, String sub) {
        return new HouseAnalysisRequest(code, mountain, main, sub, null, null, null, null, "202608");
    }
}
