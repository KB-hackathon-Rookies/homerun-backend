package com.homerun.domain.terms;

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

    @Transactional(readOnly = true)
    public List<TermResponse> getRequiredTerms() {
        return activeRequiredTerms().stream().map(TermResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AgreementStatusResponse getMyStatus(Long memberId) {
        return status(memberId, activeRequiredTerms());
    }

    @Transactional(readOnly = true)
    public boolean hasAgreedAllRequired(Long memberId) {
        return status(memberId, activeRequiredTerms()).allRequiredAgreed();
    }

    @Transactional
    public AgreementStatusResponse agreeRequiredTerms(Long memberId, RequiredAgreementRequest request) {
        List<Term> requiredTerms = activeRequiredTerms();
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

        for (Term term : requiredTerms) {
            UserAgreement agreement = agreementRepository
                    .findByMemberIdAndTermId(memberId, term.getId())
                    .orElseGet(() -> agreementRepository.save(UserAgreement.agree(memberId, term)));
            if (!agreement.isAgreed()) {
                agreement.agree();
            }
        }
        return status(memberId, requiredTerms);
    }

    private AgreementStatusResponse status(Long memberId, List<Term> requiredTerms) {
        Map<Long, UserAgreement> agreementByTermId = agreementRepository.findAllByMemberId(memberId).stream()
                .collect(Collectors.toMap(
                        agreement -> agreement.getTerm().getId(), Function.identity(), (left, right) -> right));
        List<AgreementStatusResponse.Item> items = requiredTerms.stream()
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
        return new AgreementStatusResponse(
                !items.isEmpty() && items.stream().allMatch(AgreementStatusResponse.Item::agreed), items);
    }

    private List<Term> activeRequiredTerms() {
        return termRepository.findActiveRequiredTerms(LocalDate.now());
    }
}
