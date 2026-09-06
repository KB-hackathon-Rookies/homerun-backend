package com.homerun.domain.property.type;

import static com.homerun.domain.property.type.BuildingVerdict.CHECK_RESIDENTIAL;
import static com.homerun.domain.property.type.BuildingVerdict.ILLEGAL_BUILDING;
import static com.homerun.domain.property.type.BuildingVerdict.MULTI_FAMILY;
import static com.homerun.domain.property.type.BuildingVerdict.OK;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** BR-10a 건축물대장 자동 판정. 우선순위(위반·근생 → 오피스텔 → 다가구 → OK)가 핵심이다. */
class BuildingVerdictTest {

    @Test
    void should_beIllegal_when_violationBuilding() {
        assertThat(BuildingVerdict.of(true, null, "APARTMENT", null)).isEqualTo(ILLEGAL_BUILDING);
    }

    @Test
    void should_beIllegal_when_nonResidential() {
        // 근생은 오피스텔이어도 불가가 먼저다.
        assertThat(BuildingVerdict.of(null, true, "OFFICETEL", null)).isEqualTo(ILLEGAL_BUILDING);
    }

    @Test
    void should_prioritizeIllegalOverCheckResidential() {
        // 위반건축물 오피스텔은 CHECK 가 아니라 ILLEGAL 이다.
        assertThat(BuildingVerdict.of(true, null, "OFFICETEL", null)).isEqualTo(ILLEGAL_BUILDING);
    }

    @Test
    void should_beCheckResidential_forOfficetel() {
        assertThat(BuildingVerdict.of(false, false, "OFFICETEL", false)).isEqualTo(CHECK_RESIDENTIAL);
    }

    @Test
    void should_beMultiFamily_forDaGaGu_whenNotOfficetel() {
        assertThat(BuildingVerdict.of(false, false, "VILLA", true)).isEqualTo(MULTI_FAMILY);
    }

    @Test
    void should_beOk_forResidentialApartment() {
        assertThat(BuildingVerdict.of(false, false, "APARTMENT", false)).isEqualTo(OK);
    }

    @Test
    void should_beOk_when_nothingKnownYet() {
        // 위반·근생·오피스텔·다가구 어느 신호도 없으면 OK(등기부 확인 대기). 차단은 신호등이 한다.
        assertThat(BuildingVerdict.of(null, null, "VILLA", null)).isEqualTo(OK);
    }
}
