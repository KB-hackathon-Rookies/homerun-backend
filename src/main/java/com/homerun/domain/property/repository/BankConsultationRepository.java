package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.BankConsultation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConsultationRepository extends JpaRepository<BankConsultation, Long> {

    List<BankConsultation> findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(Long planId, Long propertyId);

    Optional<BankConsultation> findByIdAndPlanIdAndPropertyId(Long id, Long planId, Long propertyId);

    void deleteByPlanIdAndPropertyId(Long planId, Long propertyId);
}
