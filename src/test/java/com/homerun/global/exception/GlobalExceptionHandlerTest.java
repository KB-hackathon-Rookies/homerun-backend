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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

        @GetMapping("/unknown")
        void unknown() {
            throw new IllegalStateException("노출되면 안 되는 내부 메시지");
        }
    }

    record TestRequest(@NotBlank(message = "이름은 필수입니다.") String name) {}
}
