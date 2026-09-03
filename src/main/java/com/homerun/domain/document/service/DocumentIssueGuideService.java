package com.homerun.domain.document.service;

import com.homerun.domain.document.dto.DocumentDtos.DocumentIssueGuide;
import com.homerun.domain.document.dto.DocumentDtos.DocumentList;
import com.homerun.domain.document.dto.DocumentDtos.DocumentSummary;
import com.homerun.domain.document.dto.DocumentDtos.IssueMethodView;
import com.homerun.domain.document.entity.DocumentIssueMethod;
import com.homerun.domain.document.entity.DocumentType;
import com.homerun.domain.document.repository.DocumentIssueMethodRepository;
import com.homerun.domain.document.repository.DocumentTypeRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서류 발급 안내(ISS-01).
 *
 * <p>온라인을 먼저 권한다(ISS-01-02). 취향 문제가 아니라 대체로 무료이거나 더 싸고, 줄을
 * 서지 않아도 되기 때문이다. 다만 <b>온라인이 안 되는 서류가 있다</b> — 전입세대확인서는
 * 주민센터에 직접 가야 한다. 그런 경우를 조용히 넘기지 않고 따로 알린다.
 */
@Service
public class DocumentIssueGuideService {

    private final DocumentTypeRepository documents;
    private final DocumentIssueMethodRepository methods;

    public DocumentIssueGuideService(DocumentTypeRepository documents, DocumentIssueMethodRepository methods) {
        this.documents = documents;
        this.methods = methods;
    }

    /** 서류 목록(ISS-01-01). */
    @Transactional(readOnly = true)
    public DocumentList list() {
        List<DocumentType> all = documents.findAllByOrderByIdAsc();
        Map<Long, List<DocumentIssueMethod>> byDocument =
                methods
                        .findByDocumentTypeIdInOrderBySortOrderAsc(
                                all.stream().map(DocumentType::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(DocumentIssueMethod::getDocumentTypeId));

        return new DocumentList(all.stream()
                .map(document -> new DocumentSummary(
                        document.getCode(),
                        document.getName(),
                        document.getIssuer(),
                        hasOnline(byDocument.getOrDefault(document.getId(), List.of())),
                        cheapestFee(byDocument.getOrDefault(document.getId(), List.of())),
                        document.getValidityDays()))
                .toList());
    }

    /** 서류 하나의 발급 안내(ISS-01-01 ~ ISS-01-05). */
    @Transactional(readOnly = true)
    public DocumentIssueGuide guide(String code) {
        DocumentType document =
                documents.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        List<DocumentIssueMethod> issueMethods = methods.findByDocumentTypeIdOrderBySortOrderAsc(document.getId());

        return new DocumentIssueGuide(
                document.getCode(),
                document.getName(),
                document.getIssuer(),
                document.getValidityDays(),
                document.getNote(),
                !issueMethods.isEmpty() && !hasOnline(issueMethods),
                toViews(issueMethods));
    }

    /**
     * 권하는 순서대로 만든다(ISS-01-02).
     *
     * <p>온라인이 있으면 그것이 권장이고, 없으면 가장 앞선 방법이 권장이다. 온라인이 없다고
     * 권장을 비워 두면 방문밖에 없는 서류에서 사용자가 무엇부터 할지 알 수 없다.
     */
    private List<IssueMethodView> toViews(List<DocumentIssueMethod> issueMethods) {
        if (issueMethods.isEmpty()) {
            return List.of();
        }
        List<DocumentIssueMethod> ordered = issueMethods.stream()
                .sorted(Comparator.comparing(DocumentIssueMethod::online)
                        .reversed()
                        .thenComparingInt(DocumentIssueMethod::getSortOrder))
                .toList();
        DocumentIssueMethod recommended = ordered.get(0);

        return ordered.stream()
                .map(issueMethod -> new IssueMethodView(
                        issueMethod.getMethod(),
                        issueMethod.getMethod().label(),
                        issueMethod.getAgency(),
                        issueMethod.getUrl(),
                        issueMethod.getFee(),
                        issueMethod.getRequirements(),
                        issueMethod.getNote(),
                        issueMethod.getId().equals(recommended.getId())))
                .toList();
    }

    private boolean hasOnline(List<DocumentIssueMethod> issueMethods) {
        return issueMethods.stream().anyMatch(DocumentIssueMethod::online);
    }

    private int cheapestFee(List<DocumentIssueMethod> issueMethods) {
        return issueMethods.stream().mapToInt(DocumentIssueMethod::getFee).min().orElse(0);
    }
}
