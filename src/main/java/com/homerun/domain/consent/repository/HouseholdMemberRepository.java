package com.homerun.domain.consent.repository;

import com.homerun.domain.consent.entity.HouseholdMember;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdMemberRepository extends JpaRepository<HouseholdMember, Long> {

    List<HouseholdMember> findByPlanIdOrderById(Long planId);
}
