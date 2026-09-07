package com.homerun.domain.education.repository;

import com.homerun.domain.education.entity.EducationContent;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationContentRepository extends JpaRepository<EducationContent, Long> {

    List<EducationContent> findByActiveTrueOrderByIdAsc();

    Optional<EducationContent> findByCodeAndActiveTrue(String code);
}
