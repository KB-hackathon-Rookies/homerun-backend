package com.homerun.domain.property.repository;

import com.homerun.domain.property.entity.PropertyCheck;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyCheckRepository extends JpaRepository<PropertyCheck, Long> {

    List<PropertyCheck> findByPropertyIdOrderById(Long propertyId);

    void deleteByPropertyId(Long propertyId);
}
