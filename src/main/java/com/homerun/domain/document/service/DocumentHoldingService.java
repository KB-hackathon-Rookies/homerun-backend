package com.homerun.domain.document.service;

import com.homerun.domain.document.dto.HoldingDtos.HoldingList;
import com.homerun.domain.document.dto.HoldingDtos.HoldingView;
import com.homerun.domain.document.dto.HoldingDtos.RecordRequest;
import com.homerun.domain.document.entity.DocumentType;
import com.homerun.domain.document.entity.UserDocument;
import com.homerun.domain.document.repository.DocumentTypeRepository;
import com.homerun.domain.document.repository.UserDocumentRepository;
import com.homerun.domain.document.type.DocumentHoldingStatus;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 서류 보유 상태와 유효기간(EVI-01-04 · EVI-01-07).
 *
 * <p>서류는 떼고 끝이 아니다. 등기부·주민등록등본·가족관계증명서는 1개월 이내 발급분만
 * 인정된다(FCT-112). 은행 상담 D-21 에 맞춰 미리 떼 두면 잔금일에는 이미 만료다.
 *
 * <p>다만 <b>인정 기간을 아는 서류만 만료를 따진다.</b> 근거가 있는 세 종류에만
 * {@code validityDays} 가 있고 나머지는 제출처마다 다르다. 모르는 것을 "아직 유효함"으로
 * 보여 주면 사용자가 만료된 서류를 들고 간다(NFR-01-06).
 */
@Service
public class DocumentHoldingService {

    /** 이 날 안에 만료되면 미리 알린다. 서류 발급에 하루이틀 걸리는 것을 감안한다. */
    private static final int EXPIRY_WARNING_DAYS = 7;

    private final UserDocumentRepository holdings;
    private final DocumentTypeRepository documents;
    private final PlanRepository plans;
    private final Clock clock;

    public DocumentHoldingService(
            UserDocumentRepository holdings, DocumentTypeRepository documents, PlanRepository plans, Clock clock) {
        this.holdings = holdings;
        this.documents = documents;
        this.plans = plans;
        this.clock = clock;
    }

    /** 남의 계획을 건드리지 못하게 한다(SEC-01-04). */
    private void verifyOwner(Long memberId, Long planId) {
        plans.findById(planId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PLAN_NOT_FOUND))
                .verifyOwner(memberId);
    }

    /** 서류 상태를 기록한다(EVI-01-04). 같은 서류를 다시 기록하면 덮어쓴다. */
    @Transactional
    public HoldingList record(Long memberId, Long planId, RecordRequest request) {
        verifyOwner(memberId, planId);
        if (!request.status().selectable()) {
            // 만료는 발급일로 판정하는 것이지 사용자가 고르는 상태가 아니다.
            throw new BusinessException(ErrorCode.DOCUMENT_STATUS_NOT_SELECTABLE);
        }
        DocumentType document = documents
                .findByCode(request.documentCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        UserDocument holding = holdings.findByPlanIdAndDocumentTypeId(planId, document.getId())
                .orElseGet(() -> new UserDocument(planId, document.getId()));
        holding.record(request.status(), request.issuedAt(), document.getValidityDays());
        if (request.status() == DocumentHoldingStatus.SUBMITTED) {
            holding.markSubmitted(LocalDate.now(clock));
        }
        holdings.save(holding);

        return list(memberId, planId);
    }

    /** 서류 현황(EVI-01-04, EVI-01-07). */
    @Transactional(readOnly = true)
    public HoldingList list(Long memberId, Long planId) {
        verifyOwner(memberId, planId);
        LocalDate today = LocalDate.now(clock);

        List<UserDocument> rows = holdings.findByPlanIdOrderByIdAsc(planId);
        Map<Long, DocumentType> byId =
                documents
                        .findAllById(rows.stream()
                                .map(UserDocument::getDocumentTypeId)
                                .toList())
                        .stream()
                        .collect(Collectors.toMap(DocumentType::getId, document -> document));

        List<HoldingView> views = new ArrayList<>();
        List<String> needsReissue = new ArrayList<>();
        List<String> expiringSoon = new ArrayList<>();

        for (UserDocument holding : rows) {
            DocumentType document = byId.get(holding.getDocumentTypeId());
            if (document == null) {
                continue;
            }
            HoldingView view = toView(holding, document, today);
            views.add(view);
            if (view.status() == DocumentHoldingStatus.EXPIRED) {
                needsReissue.add(document.getCode());
            } else if (view.expiringSoon()) {
                expiringSoon.add(document.getCode());
            }
        }
        return new HoldingList(views, needsReissue, expiringSoon);
    }

    private HoldingView toView(UserDocument holding, DocumentType document, LocalDate today) {
        boolean expired = holding.expiredOn(today);
        DocumentHoldingStatus status = expired ? DocumentHoldingStatus.EXPIRED : holding.getStatus();

        Long daysUntilExpiry =
                holding.getExpiresAt() == null ? null : ChronoUnit.DAYS.between(today, holding.getExpiresAt());
        boolean expiringSoon =
                !expired && status.held() && daysUntilExpiry != null && daysUntilExpiry <= EXPIRY_WARNING_DAYS;

        return new HoldingView(
                document.getCode(),
                document.getName(),
                status,
                status.label(),
                holding.getIssuedAt(),
                holding.getExpiresAt(),
                daysUntilExpiry,
                expiringSoon,
                validityNote(document, holding),
                action(status, expiringSoon, daysUntilExpiry));
    }

    /** 인정 기간을 모르면 그렇다고 말한다. 비워 두면 화면에서 "제한 없음"으로 읽힌다. */
    private String validityNote(DocumentType document, UserDocument holding) {
        if (document.getValidityDays() != null) {
            return null;
        }
        return holding.getStatus().held() ? "인정 기간이 제출처마다 다르다. 접수처에 확인한다." : null;
    }

    private String action(DocumentHoldingStatus status, boolean expiringSoon, Long daysUntilExpiry) {
        return switch (status) {
            case EXPIRED -> "인정 기간이 지났다. 다시 발급받는다.";
            case NEEDED -> "아직 준비하지 않았다.";
            case IN_PROGRESS -> "발급을 기다리는 중이다.";
            default -> expiringSoon ? "%d일 뒤 만료된다. 제출 일정을 앞당기거나 다시 뗀다.".formatted(daysUntilExpiry) : null;
        };
    }
}
