package com.homerun.global.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.TestcontainersConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 메트릭은 <b>기본으로 잠겨 있어야 한다.</b> /actuator/prometheus 는 등록된 엔드포인트 이름과 JVM
 * 상태를 그대로 뱉는다 — 공개하면 공격할 자리를 목록으로 알려 주는 셈이다.
 *
 * <p>노출 설정(exposure.include)에 prometheus 를 켠 상태에서 확인한다. 엔드포인트가 아예 없어서
 * 404 가 나는 것을 "안전하다" 고 착각하지 않기 위해서다. 짝이 되는
 * {@link PrometheusEndpointPublicTest} 가 플래그를 켠 쪽을 고정한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=prometheus-locked-test-secret-must-be-at-least-32-bytes",
            "management.endpoints.web.exposure.include=health,info,prometheus,metrics"
        })
class PrometheusEndpointLockedTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("플래그가 없으면 메트릭은 인증을 요구한다")
    void should_requireAuthentication_when_metricsPublicIsNotSet() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }

    @Test
    @DisplayName("헬스체크는 플래그와 무관하게 열려 있다")
    void should_keepHealthOpen_regardlessOfFlag() throws Exception {
        // 로드밸런서가 토큰 없이 부르는 자리다. 이 변경이 헬스체크까지 잠그면 배포가 멈춘다.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getStatus())
                        .isNotEqualTo(401));
    }
}
