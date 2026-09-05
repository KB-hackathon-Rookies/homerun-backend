package com.homerun.domain.application.repository;

import com.homerun.domain.application.entity.PolicyApplication;
import com.homerun.domain.application.type.ApplicationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyApplicationRepository extends JpaRepository<PolicyApplication, Long> {

    List<PolicyApplication> findByPlanIdOrderByIdDesc(Long planId);

    /** 제출됐지만(SUBMITTED/SCREENING) 오래 결과가 없는 신청. 결과 대기 지연 넛지용. */
    List<PolicyApplication> findByStatusInAndSubmittedAtBefore(
            Collection<ApplicationStatus> statuses, Instant submittedBefore);

    boolean existsByPlanIdAndPolicyId(Long planId, Long policyId);

    /** 경로의 계획에 실제로 속한 건만 찾는다. 남의 신청 건 ID 를 끼워 넣는 것을 막는다. */
    Optional<PolicyApplication> findByIdAndPlanId(Long id, Long planId);
}
