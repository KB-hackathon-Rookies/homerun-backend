package com.homerun.domain.education.repository;

import com.homerun.domain.education.entity.EducationProgress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationProgressRepository extends JpaRepository<EducationProgress, Long> {

    Optional<EducationProgress> findByMemberIdAndContentId(Long memberId, Long contentId);

    List<EducationProgress> findByMemberId(Long memberId);
}
