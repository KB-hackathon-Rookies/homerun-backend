package com.homerun.domain.policy.repository;

import com.homerun.domain.policy.entity.Policy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRepository extends JpaRepository<Policy, Long> {
    Optional<Policy> findByCode(String code);
}
