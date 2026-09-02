package com.homerun.domain.consent;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentTokenRepository extends JpaRepository<ConsentToken, Long> {

    Optional<ConsentToken> findByTokenHash(String tokenHash);

    List<ConsentToken> findByPlanIdOrderByIdDesc(Long planId);

    List<ConsentToken> findByMemberIdOrderByIdDesc(Long memberId);
}
