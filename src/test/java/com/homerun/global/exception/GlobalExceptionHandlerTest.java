package com.homerun.global.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.homerun.global.response.FieldErrorDetail;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void should_returnFieldErrors_when_validationFails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("이름은 필수입니다."));
    }

    @Test
    void should_returnBusinessError_when_businessExceptionOccurs() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLAN_001"))
                .andExpect(jsonPath("$.message").value("규칙 버전이 변경되었습니다."));
    }

    @Test
    void should_returnFieldErrors_when_stepCompletionInputIsMissing() throws Exception {
        mockMvc.perform(get("/test/field-validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PLAN_013"))
                .andExpect(jsonPath("$.message").value("단계 완료에 필요한 입력을 확인해 주세요."))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("monthlyRent"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("값을 입력하거나 모름으로 표시해 주세요."));
    }

    @Test
    void should_returnBadRequest_when_jsonIsMalformed() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_002"));
    }

    /**
     * 아래 네 개는 모두 예전에 COMMON_999 500 을 내던 자리다. 클라이언트가 잘못 부른 요청이
     * 서버 장애로 보이면 프론트는 재시도를 하고 우리는 에러 로그에서 진짜 장애를 못 찾는다.
     */
    @Test
    void should_return405_when_httpMethodIsNotSupported() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON_004"));
    }

    @Test
    void should_return415_when_contentTypeIsNotSupported() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("이름"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_005"));
    }

    @Test
    void should_returnFieldError_when_requiredParameterIsMissing() throws Exception {
        mockMvc.perform(get("/test/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_001"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("keyword"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("필수 파라미터입니다."));
    }

    @Test
    void should_return404_when_pathIsNotMapped() throws Exception {
        mockMvc.perform(get("/test/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_006"));
    }

    /**
     * 정적 리소스 매핑이 켜진 실제 부팅에서는 없는 주소가 {@code NoHandlerFoundException} 이 아니라
     * 이 예외로 온다. 위 테스트만 두면 실제 서버에서는 여전히 500 인 상태를 놓친다.
     */
    @Test
    void should_return404_when_staticResourceIsNotFound() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_006"));
    }

    @Test
    void should_hideInternalDetails_when_unknownExceptionOccurs() throws Exception {
        mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_999"))
                .andExpect(jsonPath("$.message").value("서버 오류가 발생했습니다."));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @PostMapping("/validation")
        void validate(@Valid @RequestBody TestRequest request) {}

        @GetMapping("/business")
        void business() {
            throw new BusinessException(ErrorCode.RULE_VERSION_MISMATCH);
        }

        @GetMapping("/field-validation")
        void fieldValidation() {
            throw new FieldValidationException(
                    ErrorCode.PLAN_REQUIRED_INPUT_MISSING,
                    List.of(new FieldErrorDetail("monthlyRent", "값을 입력하거나 모름으로 표시해 주세요.")));
        }

        @GetMapping("/search")
        void search(@RequestParam String keyword) {}

        @GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/test/missing-resource", "missing-resource");
        }

        @GetMapping("/unknown")
        void unknown() {
            throw new IllegalStateException("노출되면 안 되는 내부 메시지");
        }
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}
}
