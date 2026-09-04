package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.RejectionReason;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RejectionReasonRepository extends JpaRepository<RejectionReason, Long> {
    List<RejectionReason> findByVerdictId(Long verdictId);

    void deleteByVerdictId(Long verdictId);
}
