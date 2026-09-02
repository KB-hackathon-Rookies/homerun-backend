package com.homerun.domain.consent.repository;

import com.homerun.domain.consent.entity.ConsentToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsentTokenRepository extends JpaRepository<ConsentToken, Long> {

    Optional<ConsentToken> findByTokenHash(String tokenHash);

    List<ConsentToken> findByPlanIdOrderByIdDesc(Long planId);

    /** 경로의 계획에 실제로 속한 건만 찾는다. 남의 동의 건 ID 를 끼워 넣는 것을 막는다. */
    Optional<ConsentToken> findByIdAndPlanId(Long id, Long planId);

    List<ConsentToken> findByMemberIdOrderByIdDesc(Long memberId);

    /**
     * 같은 가구원의 아직 살아 있는 링크를 모두 폐기한다.
     *
     * <p>발급할 때마다 이전 것을 끊지 않으면 72시간짜리 유효한 링크가 여러 개 생긴다.
     * "유효한 링크는 하나"라는 불변식이 재발급에서만 지켜지고 발급에서는 새 나간다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ConsentToken t SET t.revokedAt = :now
            WHERE t.memberId = :memberId AND t.usedAt IS NULL AND t.revokedAt IS NULL
            """)
    int revokeActiveByMemberId(@Param("memberId") Long memberId, @Param("now") Instant now);

    /**
     * 아직 쓰지 않은 토큰을 사용 처리한다.
     *
     * <p>조회와 갱신을 나누면 동시 요청 두 개가 모두 usedAt == null 을 읽고 성공한다.
     * 조건을 UPDATE 문 안에 넣어 한 번만 1행이 갱신되게 한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE ConsentToken t SET t.usedAt = :now
            WHERE t.id = :id AND t.usedAt IS NULL AND t.revokedAt IS NULL
            """)
    int markUsedIfUnused(@Param("id") Long id, @Param("now") Instant now);
}
