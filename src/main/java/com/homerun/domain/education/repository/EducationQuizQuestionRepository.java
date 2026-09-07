package com.homerun.domain.education.repository;

import com.homerun.domain.education.entity.EducationQuizQuestion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationQuizQuestionRepository extends JpaRepository<EducationQuizQuestion, Long> {

    List<EducationQuizQuestion> findByContentIdOrderBySortOrderAsc(Long contentId);
}
