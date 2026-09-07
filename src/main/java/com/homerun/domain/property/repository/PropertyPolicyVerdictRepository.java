package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.PropertyPolicyVerdict;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyPolicyVerdictRepository extends JpaRepository<PropertyPolicyVerdict, Long> {

    List<PropertyPolicyVerdict> findByPropertyIdOrderByIdAsc(Long propertyId);

    Optional<PropertyPolicyVerdict> findByPropertyIdAndPolicyId(Long propertyId, Long policyId);

    void deleteByPropertyId(Long propertyId);
}
