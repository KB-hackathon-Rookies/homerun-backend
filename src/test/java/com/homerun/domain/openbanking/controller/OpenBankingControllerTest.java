package com.homerun.domain.openbanking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.dto.response.OpenBankingAuthorizationResponse;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class OpenBankingControllerTest {

    @Test
    void should_returnAuthorizationUrlAndKeepOAuthStateInSession() {
        OpenBankingService service = mock(OpenBankingService.class);
        when(service.authorizationUri(anyString()))
                .thenReturn(URI.create("https://testapi.openbanking.or.kr/oauth/2.0/authorize?state=test"));
        OpenBankingController controller = new OpenBankingController(service);
        MockHttpSession session = new MockHttpSession();

        ApiResponse<OpenBankingAuthorizationResponse> response = controller.connect(new MemberPrincipal(7L), session);

        assertThat(response.data().authorizationUrl())
                .isEqualTo("https://testapi.openbanking.or.kr/oauth/2.0/authorize?state=test");
        assertThat(session.getAttribute("open-banking.oauth.state")).isNotNull();
        assertThat(session.getAttribute("open-banking.oauth.member-id")).isEqualTo(7L);
    }
}
