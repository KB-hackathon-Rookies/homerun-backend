package com.homerun.global.security;

import static org.assertj.core.api.Assertions.assertThat;
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
 * 플래그를 켠 쪽. 부하 시험이나 로컬 관측처럼 스크래퍼가 같은 호스트에 있을 때만 켠다.
 *
 * <p>여는 범위는 /actuator/prometheus 한 자리다. 플래그 하나로 actuator 전체가 딸려 열리면
 * /actuator/metrics · /actuator/env 같은 곳까지 토큰 없이 읽히므로 그것도 함께 확인한다.
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest(
        properties = {
            "app.jwt.secret=prometheus-public-test-secret-must-be-at-least-32-bytes",
            "management.endpoints.web.exposure.include=health,info,prometheus,metrics",
            "management.metrics-public=true"
        })
class PrometheusEndpointPublicTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("플래그를 켜면 토큰 없이 메트릭을 긁을 수 있다")
    void should_permitPrometheus_when_metricsPublicIsTrue() throws Exception {
        String body = mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        // 200 만 보면 빈 응답도 통과한다. 레지스트리가 실제로 붙어 있는지까지 본다.
        assertThat(body).contains("jvm_memory_used_bytes");
    }

    @Test
    @DisplayName("플래그를 켜도 다른 actuator 엔드포인트는 잠긴 채로 남는다")
    void should_keepOtherActuatorEndpointsLocked_when_metricsPublicIsTrue() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }
}
