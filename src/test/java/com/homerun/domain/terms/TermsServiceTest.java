package com.homerun.domain.terms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class TermsServiceTest {

    private static final Long MEMBER_ID = 1L;

    @Mock
    private TermRepository termRepository;

    @Mock
    private UserAgreementRepository agreementRepository;

    private TermsService termsService;
    private List<Term> requiredTerms;

    @BeforeEach
    void setUp() {
        termsService = new TermsService(termRepository, agreementRepository);
        requiredTerms =
                List.of(term(1L, "SERVICE_TERMS", "1.0", "서비스 이용약관"), term(2L, "PRIVACY_POLICY", "1.0", "개인정보 처리 안내"));
    }

    @Test
    void should_rejectAgreement_when_requiredTermIsMissing() {
        when(termRepository.findActiveRequiredTerms(any(LocalDate.class))).thenReturn(requiredTerms);
        RequiredAgreementRequest request =
                new RequiredAgreementRequest(List.of(new AgreementItem("SERVICE_TERMS", "1.0", true)));

        assertThatThrownBy(() -> termsService.agreeRequiredTerms(MEMBER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_AGREED));
    }

    @Test
    void should_rejectAgreement_when_versionIsOutdated() {
        when(termRepository.findActiveRequiredTerms(any(LocalDate.class))).thenReturn(requiredTerms);
        RequiredAgreementRequest request = new RequiredAgreementRequest(List.of(
                new AgreementItem("SERVICE_TERMS", "0.9", true), new AgreementItem("PRIVACY_POLICY", "1.0", true)));

        assertThatThrownBy(() -> termsService.agreeRequiredTerms(MEMBER_ID, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.TERMS_VERSION_MISMATCH));
    }

    @Test
    void should_storeEachAgreementOnce_when_sameAgreementIsRequestedAgain() {
        List<UserAgreement> stored = new ArrayList<>();
        when(termRepository.findActiveRequiredTerms(any(LocalDate.class))).thenReturn(requiredTerms);
        when(agreementRepository.findByMemberIdAndTermId(anyLong(), anyLong()))
                .thenAnswer(invocation -> stored.stream()
                        .filter(agreement -> agreement.getTerm().getId().equals(invocation.getArgument(1)))
                        .findFirst());
        when(agreementRepository.save(any(UserAgreement.class))).thenAnswer(invocation -> {
            UserAgreement agreement = invocation.getArgument(0);
            stored.add(agreement);
            return agreement;
        });
        when(agreementRepository.findAllByMemberId(MEMBER_ID)).thenAnswer(invocation -> List.copyOf(stored));
        RequiredAgreementRequest request = new RequiredAgreementRequest(List.of(
                new AgreementItem("SERVICE_TERMS", "1.0", true), new AgreementItem("PRIVACY_POLICY", "1.0", true)));

        AgreementStatusResponse first = termsService.agreeRequiredTerms(MEMBER_ID, request);
        AgreementStatusResponse second = termsService.agreeRequiredTerms(MEMBER_ID, request);

        assertThat(first.allRequiredAgreed()).isTrue();
        assertThat(second.allRequiredAgreed()).isTrue();
        assertThat(second.agreements()).hasSize(2).allMatch(AgreementStatusResponse.Item::agreed);
        verify(agreementRepository, times(2)).save(any(UserAgreement.class));
    }

    private Term term(Long id, String code, String version, String title) {
        Term term = new Term();
        ReflectionTestUtils.setField(term, "id", id);
        ReflectionTestUtils.setField(term, "code", code);
        ReflectionTestUtils.setField(term, "version", version);
        ReflectionTestUtils.setField(term, "title", title);
        ReflectionTestUtils.setField(term, "required", true);
        ReflectionTestUtils.setField(term, "contentUrl", "/terms/" + code.toLowerCase());
        ReflectionTestUtils.setField(term, "effectiveFrom", LocalDate.of(2026, 9, 2));
        return term;
    }
}
