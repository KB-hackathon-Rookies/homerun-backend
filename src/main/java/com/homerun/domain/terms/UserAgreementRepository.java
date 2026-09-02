package com.homerun.domain.terms;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgreementRepository extends JpaRepository<UserAgreement, Long> {
    List<UserAgreement> findAllByMemberId(Long memberId);

    Optional<UserAgreement> findByMemberIdAndTermId(Long memberId, Long termId);
}
