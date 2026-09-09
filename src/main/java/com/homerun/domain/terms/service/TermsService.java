package com.homerun.domain.terms.service;

import com.homerun.domain.terms.dto.request.AgreementItem;
import com.homerun.domain.terms.dto.request.RequiredAgreementRequest;
import com.homerun.domain.terms.dto.response.AgreementStatusResponse;
import com.homerun.domain.terms.dto.response.TermResponse;
import com.homerun.domain.terms.entity.Term;
import com.homerun.domain.terms.entity.UserAgreement;
import com.homerun.domain.terms.repository.TermRepository;
import com.homerun.domain.terms.repository.UserAgreementRepository;
import com.homerun.domain.terms.type.TermScope;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TermsService {

    private final TermRepository termRepository;
    private final UserAgreementRepository agreementRepository;

    public TermsService(TermRepository termRepository, UserAgreementRepository agreementRepository) {
        this.termRepository = termRepository;
        this.agreementRepository = agreementRepository;
    }

    /**
     * 그 범위의 약관 목록. <b>선택 항목도 준다</b> — 화면이 선택 동의를 그리려면 목록에 있어야 한다.
     */
    @Transactional(readOnly = true)
    public List<TermResponse> getTerms(TermScope scope) {
        return termRepository.findActiveTerms(scope, LocalDate.now()).stream()
                .map(TermResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AgreementStatusResponse getMyStatus(Long memberId, TermScope scope) {
        return status(memberId, termRepository.findActiveTerms(scope, LocalDate.now()));
    }

    /**
     * 앱을 쓸 수 있는가. <b>{@code SERVICE} 범위만 본다.</b>
     *
     * <p>필터가 이 값으로 모든 요청을 막는다. 기능별 약관을 여기에 섞으면 그 기능을 쓰지 않는
     * 사용자까지 앱 전체가 멈춘다.
     */
    @Transactional(readOnly = true)
    public boolean hasAgreedAllRequired(Long memberId) {
        return requiredAgreed(memberId, TermScope.SERVICE);
    }

    /** 그 범위의 <b>필수</b> 약관을 다 동의했는가. 선택 항목은 보지 않는다. */
    @Transactional(readOnly = true)
    public boolean requiredAgreed(Long memberId, TermScope scope) {
        List<Term> required = activeRequiredTerms(scope);
        if (required.isEmpty()) {
            return true;
        }
        return status(memberId, required).allRequiredAgreed();
    }

    @Transactional
    public AgreementStatusResponse agree(Long memberId, TermScope scope, RequiredAgreementRequest request) {
        List<Term> scopeTerms = termRepository.findActiveTerms(scope, LocalDate.now());
        List<Term> requiredTerms = scopeTerms.stream().filter(Term::isRequired).toList();
        Map<String, AgreementItem> requestedByCode = request.agreements().stream()
                .collect(Collectors.toMap(AgreementItem::code, Function.identity(), (left, right) -> right));

        for (Term term : requiredTerms) {
            AgreementItem item = requestedByCode.get(term.getCode());
            if (item == null || !Boolean.TRUE.equals(item.agreed())) {
                throw new BusinessException(ErrorCode.REQUIRED_TERMS_NOT_AGREED);
            }
            if (!term.getVersion().equals(item.version())) {
                throw new BusinessException(ErrorCode.TERMS_VERSION_MISMATCH);
            }
        }

        /*
         * 선택 항목도 요청에 담겨 온 대로 남긴다. 필수만 기록하면 "분석·저장에 동의했는가" 를
         * 나중에 알 방법이 없다 — 동의하지 않은 것과 물어본 적 없는 것이 구분되지 않는다.
         */
        for (Term term : scopeTerms) {
            AgreementItem item = requestedByCode.get(term.getCode());
            if (item == null) {
                continue;
            }
            UserAgreement agreement = agreementRepository
                    .findByMemberIdAndTermId(memberId, term.getId())
                    .orElseGet(() -> agreementRepository.save(UserAgreement.of(memberId, term)));
            if (Boolean.TRUE.equals(item.agreed())) {
                agreement.agree();
            } else {
                agreement.revoke();
            }
        }
        return status(memberId, scopeTerms);
    }

    /**
     * 동의 상태. {@code terms} 에는 선택 항목이 섞여 있을 수 있다.
     *
     * <p>그래서 {@code allRequiredAgreed} 는 <b>필수만</b> 보고 낸다. 전부를 보면 선택을 거절한
     * 사용자가 필수까지 미충족으로 잡혀 화면이 막힌다.
     */
    private AgreementStatusResponse status(Long memberId, List<Term> terms) {
        Map<Long, UserAgreement> agreementByTermId = agreementRepository.findAllByMemberId(memberId).stream()
                .collect(Collectors.toMap(
                        agreement -> agreement.getTerm().getId(), Function.identity(), (left, right) -> right));
        List<AgreementStatusResponse.Item> items = terms.stream()
                .map(term -> {
                    UserAgreement agreement = agreementByTermId.get(term.getId());
                    boolean agreed = agreement != null && agreement.isAgreed();
                    return new AgreementStatusResponse.Item(
                            term.getCode(),
                            term.getVersion(),
                            term.isRequired(),
                            agreed,
                            agreed ? agreement.getAgreedAt() : null);
                })
                .toList();
        List<AgreementStatusResponse.Item> required =
                items.stream().filter(AgreementStatusResponse.Item::required).toList();
        return new AgreementStatusResponse(
                !required.isEmpty() && required.stream().allMatch(AgreementStatusResponse.Item::agreed), items);
    }

    private List<Term> activeRequiredTerms(TermScope scope) {
        return termRepository.findActiveRequiredTerms(scope, LocalDate.now());
    }
}
