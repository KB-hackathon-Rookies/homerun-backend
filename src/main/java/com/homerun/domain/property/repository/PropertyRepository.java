package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    boolean existsByIdAndPlanId(Long id, Long planId);
}
