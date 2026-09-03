package com.homerun.domain.document.repository;

import com.homerun.domain.document.entity.DocumentType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentTypeRepository extends JpaRepository<DocumentType, Long> {

    Optional<DocumentType> findByCode(String code);

    List<DocumentType> findAllByOrderByIdAsc();
}
