package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.BankConsultation;
import com.homerun.domain.property.type.ConsultedLoanProduct;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankConsultationRepository extends JpaRepository<BankConsultation, Long> {

    List<BankConsultation> findAllByPlanIdAndPropertyIdOrderByConsultedAtDescIdDesc(Long planId, Long propertyId);

    Optional<BankConsultation> findByIdAndPlanIdAndPropertyId(Long id, Long planId, Long propertyId);

    Optional<BankConsultation> findByPlanIdAndPropertyIdAndBankNameAndLoanProduct(
            Long planId, Long propertyId, String bankName, ConsultedLoanProduct loanProduct);

    void deleteByPlanIdAndPropertyId(Long planId, Long propertyId);
}
