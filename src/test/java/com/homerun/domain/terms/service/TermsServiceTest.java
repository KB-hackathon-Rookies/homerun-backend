package com.homerun.domain.terms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.terms.dto.request.AgreementItem;
import com.homerun.domain.terms.dto.request.RequiredAgreementRequest;
import com.homerun.domain.terms.dto.response.AgreementStatusResponse;
import com.homerun.domain.terms.entity.Term;
import com.homerun.domain.terms.entity.UserAgreement;
import com.homerun.domain.terms.repository.TermRepository;
import com.homerun.domain.terms.repository.UserAgreementRepository;
import com.homerun.domain.terms.type.TermScope;
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
import org.springframework.beans.BeanUtils;
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
        when(termRepository.findActiveTerms(eq(TermScope.SERVICE), any(LocalDate.class)))
                .thenReturn(requiredTerms);
        RequiredAgreementRequest request =
                new RequiredAgreementRequest(List.of(new AgreementItem("SERVICE_TERMS", "1.0", true)));

        assertThatThrownBy(() -> termsService.agree(MEMBER_ID, TermScope.SERVICE, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.REQUIRED_TERMS_NOT_AGREED));
    }

    @Test
    void should_rejectAgreement_when_versionIsOutdated() {
        when(termRepository.findActiveTerms(eq(TermScope.SERVICE), any(LocalDate.class)))
                .thenReturn(requiredTerms);
        RequiredAgreementRequest request = new RequiredAgreementRequest(List.of(
                new AgreementItem("SERVICE_TERMS", "0.9", true), new AgreementItem("PRIVACY_POLICY", "1.0", true)));

        assertThatThrownBy(() -> termsService.agree(MEMBER_ID, TermScope.SERVICE, request))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.TERMS_VERSION_MISMATCH));
    }

    @Test
    void should_storeEachAgreementOnce_when_sameAgreementIsRequestedAgain() {
        List<UserAgreement> stored = new ArrayList<>();
        when(termRepository.findActiveTerms(eq(TermScope.SERVICE), any(LocalDate.class)))
                .thenReturn(requiredTerms);
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

        AgreementStatusResponse first = termsService.agree(MEMBER_ID, TermScope.SERVICE, request);
        AgreementStatusResponse second = termsService.agree(MEMBER_ID, TermScope.SERVICE, request);

        assertThat(first.allRequiredAgreed()).isTrue();
        assertThat(second.allRequiredAgreed()).isTrue();
        assertThat(second.agreements()).hasSize(2).allMatch(AgreementStatusResponse.Item::agreed);
        verify(agreementRepository, times(2)).save(any(UserAgreement.class));
    }

    /**
     * 기능별 약관이 앱 전체를 막으면 안 된다. 오픈뱅킹을 쓰지 않는 사용자까지 모든 요청이
     * 403 으로 끊긴다 — 필터가 이 값 하나로 판단하기 때문이다.
     */
    @Test
    void should_ignoreFeatureTerms_when_checkingAppWideAgreement() {
        when(termRepository.findActiveRequiredTerms(eq(TermScope.SERVICE), any(LocalDate.class)))
                .thenReturn(requiredTerms);
        when(agreementRepository.findAllByMemberId(MEMBER_ID))
                .thenReturn(List.of(agreed(requiredTerms.get(0)), agreed(requiredTerms.get(1))));

        // 오픈뱅킹 약관에는 손도 대지 않았지만 앱은 열려 있어야 한다.
        assertThat(termsService.hasAgreedAllRequired(MEMBER_ID)).isTrue();
        verify(termRepository, never()).findActiveRequiredTerms(eq(TermScope.OPEN_BANKING), any(LocalDate.class));
    }

    /** 선택 항목을 거절해도 필수를 다 채웠으면 그 기능은 쓸 수 있어야 한다. */
    @Test
    void should_treatRequiredAsSatisfied_when_optionalTermIsDeclined() {
        Term required = term(10L, "OPEN_BANKING_SERVICE", "1.0", "오픈뱅킹 서비스 이용약관", true, TermScope.OPEN_BANKING);
        Term optional = term(11L, "OPEN_BANKING_ANALYSIS", "1.0", "조회 결과 분석·저장 동의", false, TermScope.OPEN_BANKING);
        List<UserAgreement> stored = new ArrayList<>();
        givenAgreementStore(stored);
        when(termRepository.findActiveTerms(eq(TermScope.OPEN_BANKING), any(LocalDate.class)))
                .thenReturn(List.of(required, optional));

        AgreementStatusResponse response = termsService.agree(
                MEMBER_ID,
                TermScope.OPEN_BANKING,
                new RequiredAgreementRequest(List.of(
                        new AgreementItem("OPEN_BANKING_SERVICE", "1.0", true),
                        new AgreementItem("OPEN_BANKING_ANALYSIS", "1.0", false))));

        assertThat(response.allRequiredAgreed()).isTrue();
        assertThat(response.agreements()).hasSize(2);
    }

    /**
     * 거절도 기록으로 남는다. 남기지 않으면 "동의하지 않음" 과 "물어본 적 없음" 이 구분되지
     * 않아, 다음에 또 물어야 할지 알 수 없다.
     */
    @Test
    void should_recordDeclinedOptionalTerm_insteadOfLeavingItBlank() {
        Term required = term(10L, "OPEN_BANKING_SERVICE", "1.0", "오픈뱅킹 서비스 이용약관", true, TermScope.OPEN_BANKING);
        Term optional = term(11L, "OPEN_BANKING_ANALYSIS", "1.0", "조회 결과 분석·저장 동의", false, TermScope.OPEN_BANKING);
        List<UserAgreement> stored = new ArrayList<>();
        givenAgreementStore(stored);
        when(termRepository.findActiveTerms(eq(TermScope.OPEN_BANKING), any(LocalDate.class)))
                .thenReturn(List.of(required, optional));

        termsService.agree(
                MEMBER_ID,
                TermScope.OPEN_BANKING,
                new RequiredAgreementRequest(List.of(
                        new AgreementItem("OPEN_BANKING_SERVICE", "1.0", true),
                        new AgreementItem("OPEN_BANKING_ANALYSIS", "1.0", false))));

        assertThat(stored).hasSize(2);
        assertThat(stored.stream().filter(UserAgreement::isAgreed)).hasSize(1);
    }

    /** 그 범위에 약관이 아직 없으면 막을 근거도 없다. */
    @Test
    void should_allowScope_when_noTermsAreRegistered() {
        when(termRepository.findActiveRequiredTerms(eq(TermScope.OPEN_BANKING), any(LocalDate.class)))
                .thenReturn(List.of());

        assertThat(termsService.requiredAgreed(MEMBER_ID, TermScope.OPEN_BANKING))
                .isTrue();
    }

    private void givenAgreementStore(List<UserAgreement> stored) {
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
    }

    private UserAgreement agreed(Term term) {
        UserAgreement agreement = UserAgreement.agree(MEMBER_ID, term);
        agreement.agree();
        return agreement;
    }

    private Term term(Long id, String code, String version, String title) {
        return term(id, code, version, title, true, TermScope.SERVICE);
    }

    private Term term(Long id, String code, String version, String title, boolean required, TermScope scope) {
        Term term = BeanUtils.instantiateClass(Term.class);
        ReflectionTestUtils.setField(term, "id", id);
        ReflectionTestUtils.setField(term, "code", code);
        ReflectionTestUtils.setField(term, "version", version);
        ReflectionTestUtils.setField(term, "title", title);
        ReflectionTestUtils.setField(term, "required", required);
        ReflectionTestUtils.setField(term, "scope", scope);
        ReflectionTestUtils.setField(term, "contentUrl", "/terms/" + code.toLowerCase());
        ReflectionTestUtils.setField(term, "effectiveFrom", LocalDate.of(2026, 9, 2));
        return term;
    }
}
