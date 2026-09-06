package com.homerun.domain.property.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.TestcontainersConfiguration;
import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.repository.PropertyRepository;
import com.homerun.domain.property.type.DataSource;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

/**
 * V43 이 더한 컬럼이 실제 Postgres 에서 왕복하는지와 CHECK 제약이 실제로 막는지 확인한다(#179).
 *
 * <p>전용면적은 BR-09 의 85㎡ 판정 입력이라 값이 없을 때 0 이 되지 않는 것이 중요하다 — 0 이면
 * 면적 조건을 통과해 버린다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PropertyAreaSourcingIntegrationTest {

    private final PropertyRepository properties;
    private final EntityManager em;

    PropertyAreaSourcingIntegrationTest(@Autowired PropertyRepository properties, @Autowired EntityManager em) {
        this.properties = properties;
        this.em = em;
    }

    @Test
    void should_roundTripAreaAndSources_againstRealPostgres() {
        Property property = properties.save(candidate(setUpPlan()));
        property.recordSourcedFacts("123-4", "401호", new BigDecimal("42.35"), DataSource.AUTO, DataSource.AUTO, true);
        em.flush();
        em.clear();

        Property reloaded = properties.findById(property.getId()).orElseThrow();
        assertThat(reloaded.getExclusiveArea()).isEqualByComparingTo("42.35");
        assertThat(reloaded.getAreaSource()).isEqualTo(DataSource.AUTO);
        assertThat(reloaded.getHouseTypeSource()).isEqualTo(DataSource.AUTO);
        assertThat(reloaded.getPriceMatched()).isTrue();
        assertThat(reloaded.getJibun()).isEqualTo("123-4");
        assertThat(reloaded.getDetailAddress()).isEqualTo("401호");
    }

    @Test
    void should_leaveAreaSourceNull_when_areaWasNeverObtained() {
        // 실거래 매칭도 없고 직접 입력도 없으면 면적을 모른다. 출처만 MANUAL 로 남으면
        // "사람이 적었다"는 거짓 기록이 된다.
        Property property = properties.save(candidate(setUpPlan()));
        property.recordSourcedFacts("123-4", "401호", null, DataSource.MANUAL, DataSource.MANUAL, false);
        em.flush();
        em.clear();

        Property reloaded = properties.findById(property.getId()).orElseThrow();
        assertThat(reloaded.getExclusiveArea()).isNull();
        assertThat(reloaded.getAreaSource()).isNull();
    }

    @Test
    void should_rejectUnknownSource_becauseEnumAndCheckConstraintArePaired() {
        Long planId = setUpPlan();

        assertThatThrownBy(() -> {
                    em.createNativeQuery("""
                            INSERT INTO property (plan_id, deposit, area_source)
                            VALUES (:pid, 100000000, 'GUESSED')
                            """).setParameter("pid", planId).executeUpdate();
                    em.flush();
                })
                .hasMessageContaining("ck_property_area_source");
    }

    private Property candidate(Long planId) {
        return Property.candidate(
                planId,
                "1162010100",
                "서울특별시 관악구 신림동 123-4",
                "서울특별시 관악구 신림로 00길 00",
                "홈런오피스텔",
                "OFFICETEL",
                180_000_000L,
                null,
                null,
                null,
                null,
                Boolean.FALSE,
                Boolean.FALSE,
                Boolean.FALSE,
                Boolean.FALSE,
                Instant.now());
    }

    private Long setUpPlan() {
        Long memberId = ((Number) em.createNativeQuery("""
                        INSERT INTO app_user (auth_provider, provider_user_id, name)
                        VALUES ('KAKAO', 'area-test-' || nextval('app_user_id_seq'), 'tester')
                        RETURNING id
                        """).getSingleResult()).longValue();
        return ((Number) em.createNativeQuery(
                                "INSERT INTO plan (user_id, lease_type) VALUES (:uid, 'JEONSE') RETURNING id")
                        .setParameter("uid", memberId)
                        .getSingleResult())
                .longValue();
    }
}
