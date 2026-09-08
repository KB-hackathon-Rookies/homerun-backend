package com.homerun.domain.openbanking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.dto.response.OpenBankingAuthorizationResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.repository.OpenBankingOAuthStateStore;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenBankingControllerTest {

    private final OpenBankingService service = mock(OpenBankingService.class);
    private final OpenBankingOAuthStateStore stateStore = mock(OpenBankingOAuthStateStore.class);
    private final OpenBankingController controller = new OpenBankingController(service, stateStore);

    @Test
    @DisplayName("연결 시작: 인가 URL 을 돌려주고 URL 의 state 와 같은 값으로 state→member 를 저장한다")
    void should_saveStateForMember_when_connect() {
        when(service.authorizationUri(anyString()))
                .thenReturn(URI.create("https://testapi.openbanking.or.kr/oauth/2.0/authorize?state=test"));

        ApiResponse<OpenBankingAuthorizationResponse> response = controller.connect(new MemberPrincipal(7L));

        assertThat(response.data().authorizationUrl())
                .isEqualTo("https://testapi.openbanking.or.kr/oauth/2.0/authorize?state=test");
        ArgumentCaptor<String> state = ArgumentCaptor.forClass(String.class);
        verify(service).authorizationUri(state.capture());
        verify(stateStore).save(state.getValue(), 7L);
    }

    @Test
    @DisplayName("콜백: state 로 회원을 찾으면 그 회원으로 연결한다")
    void should_connect_when_stateMatches() {
        OpenBankingConnectionResponse connection = new OpenBankingConnectionResponse(true, "login inquiry", null, null);
        when(stateStore.consume("st")).thenReturn(Optional.of(7L));
        when(service.connect(7L, "code")).thenReturn(connection);

        ApiResponse<OpenBankingConnectionResponse> response = controller.callback("code", "st", null);

        assertThat(response.data()).isSameAs(connection);
        verify(service).connect(7L, "code");
    }

    @Test
    @DisplayName("콜백: state 가 없거나 만료돼 회원을 못 찾으면 OPENBANKING_004")
    void should_reject_when_stateUnknown() {
        when(stateStore.consume("st")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.callback("code", "st", null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_OPEN_BANKING_REQUEST);
        verify(service, never()).connect(anyLong(), anyString());
    }

    @Test
    @DisplayName("콜백: code 가 없으면 회원 조회 없이 OPENBANKING_004")
    void should_reject_when_codeMissing() {
        assertThatThrownBy(() -> controller.callback(null, "st", null))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.INVALID_OPEN_BANKING_REQUEST);
        verify(stateStore, never()).consume(anyString());
    }

    @Test
    @DisplayName("콜백: 제공자가 error 를 주면 거부로 처리하고 남은 state 를 정리한다")
    void should_rejectAuth_when_errorPresent() {
        assertThatThrownBy(() -> controller.callback(null, "st", "access_denied"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).errorCode())
                .isEqualTo(ErrorCode.OPEN_BANKING_AUTH_REJECTED);
        verify(stateStore).consume("st");
        verify(service, never()).connect(anyLong(), eq("code"));
    }
}
