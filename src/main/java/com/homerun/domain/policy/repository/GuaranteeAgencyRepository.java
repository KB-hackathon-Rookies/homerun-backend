package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.GuaranteeAgency;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuaranteeAgencyRepository extends JpaRepository<GuaranteeAgency, Long> {

    List<GuaranteeAgency> findAllByOrderByCodeAsc();
}
