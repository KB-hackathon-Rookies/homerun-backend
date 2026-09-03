package com.homerun.domain.document.repository;

import com.homerun.domain.document.entity.UserDocument;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDocumentRepository extends JpaRepository<UserDocument, Long> {

    List<UserDocument> findByPlanIdOrderByIdAsc(Long planId);

    Optional<UserDocument> findByPlanIdAndDocumentTypeId(Long planId, Long documentTypeId);
}
