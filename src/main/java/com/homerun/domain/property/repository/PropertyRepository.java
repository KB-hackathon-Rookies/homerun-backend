package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.Property;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    boolean existsByIdAndPlanId(Long id, Long planId);

    long countByPlanId(Long planId);

    List<Property> findAllByPlanIdOrderByIdAsc(Long planId);

    Optional<Property> findByIdAndPlanId(Long id, Long planId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Property p SET p.selected = false WHERE p.planId = :planId AND p.selected = true")
    void clearSelection(@Param("planId") Long planId);
}
