package com.homerun.domain.plan.type;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.global.external.realestate.HousingType;
import org.junit.jupiter.api.Test;

/** 실거래 API 어휘(HousingType 3종)와 도메인 어휘(HouseType 6종)를 잇는 유일한 지점. */
class HouseTypeTest {

    @Test
    void should_mapRowHouseToVilla_becauseTheyAreTheSameThingInTwoVocabularies() {
        // 실거래 API 의 ROW_HOUSE 를 도메인에서는 VILLA(연립·다세대)로 부른다. 이게 어긋나면
        // 자동판별한 연립다세대가 HOUSE_TYPE 화이트리스트에 걸린다.
        assertThat(HouseType.from(HousingType.ROW_HOUSE)).isEqualTo(HouseType.VILLA);
        assertThat(HouseType.VILLA.toHousingType()).contains(HousingType.ROW_HOUSE);
    }

    @Test
    void should_roundTripEveryHousingType() {
        // 실거래 API 가 주는 3종은 전부 도메인으로 왕복돼야 한다.
        for (HousingType housingType : HousingType.values()) {
            assertThat(HouseType.from(housingType).toHousingType()).contains(housingType);
        }
    }

    @Test
    void should_haveNoTransactionEndpoint_forTypesRealEstateApiDoesNotServe() {
        // 단독·다가구·기타는 국토부가 유형별 실거래 API 를 제공하지 않는다. 억지로 엔드포인트를
        // 만들면 없는 API 를 부른다 — 비어 있음이 맞는 답이고, 부른 쪽은 매칭을 건너뛴다.
        assertThat(HouseType.DETACHED.toHousingType()).isEmpty();
        assertThat(HouseType.MULTI_FAMILY.toHousingType()).isEmpty();
        assertThat(HouseType.OTHER.toHousingType()).isEmpty();
    }
}
