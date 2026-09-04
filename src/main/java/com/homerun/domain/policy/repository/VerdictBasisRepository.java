package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.VerdictBasis;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VerdictBasisRepository extends JpaRepository<VerdictBasis, Long> {
    List<VerdictBasis> findByVerdictId(Long verdictId);

    void deleteByVerdictId(Long verdictId);
}
