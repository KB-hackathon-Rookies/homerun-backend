package com.homerun.domain.fact.repository;

import com.homerun.domain.fact.entity.ConfigEffective;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConfigEffectiveRepository extends JpaRepository<ConfigEffective, Long> {

    /**
     * 기준일에 유효한 행 하나를 찾는다.
     *
     * <p>같은 fact_code 가 시행일별로 여러 행 존재할 수 있다. 기준일을 넘지 않는 것 중
     * 가장 최근 시행분을 쓴다. effective_from 이 비어 있으면 시행일 제한이 없는 값이다.
     *
     * <p>effective_to 가 지난 행은 제외한다 — 유효기간이 끝난 한시 혜택·구 기준값을 만료일
     * 이후에도 판정 근거로 쓰지 않는다. effective_to 가 비어 있으면 만료 없는 값이다.
     */
    @Query("""
            SELECT c FROM ConfigEffective c
            WHERE c.factCode = :factCode
              AND (c.effectiveFrom IS NULL OR c.effectiveFrom <= :on)
              AND (c.effectiveTo IS NULL OR c.effectiveTo >= :on)
            ORDER BY c.effectiveFrom DESC NULLS LAST
            LIMIT 1
            """)
    Optional<ConfigEffective> findEffective(@Param("factCode") String factCode, @Param("on") LocalDate on);
}
