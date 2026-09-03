package com.homerun.domain.document.repository;

import com.homerun.domain.document.entity.DocumentIssueMethod;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentIssueMethodRepository extends JpaRepository<DocumentIssueMethod, Long> {

    List<DocumentIssueMethod> findByDocumentTypeIdOrderBySortOrderAsc(Long documentTypeId);

    List<DocumentIssueMethod> findByDocumentTypeIdInOrderBySortOrderAsc(List<Long> documentTypeIds);
}
