package com.homerun.domain.consent.controller;

import com.homerun.domain.consent.dto.ConsentDtos.ConsentView;
import com.homerun.domain.consent.dto.ConsentDtos.ResponseRequest;
import com.homerun.domain.consent.service.ConsentService;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가구원이 링크로 들어오는 화면.
 *
 * <p>가구원은 우리 서비스 회원이 아니다. 세션 토큰이 없고 동의 토큰만 들고 온다. 그래서
 * 계획 하위가 아니라 별도 경로에 두고 SecurityConfig 에서 permitAll 로 열어야 한다.
 */
@RestController
@RequestMapping("/api/v1/consents")
@Tag(name = "가구원 동의(공개)", description = "동의 토큰으로만 접근하는 가구원 화면")
public class PublicConsentController {

    private final ConsentService service;

    public PublicConsentController(ConsentService service) {
        this.service = service;
    }

    @GetMapping("/{token}")
    @Operation(summary = "가구원 동의 화면", description = "관계와 목적만 준다. 이름과 주민식별정보는 담지 않는다.")
    public ResponseEntity<ApiResponse<ConsentView>> view(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(ApiResponse.success(service.view(token)));
    }

    @PostMapping("/{token}/response")
    @Operation(summary = "가구원 동의·거절 제출", description = "응답하면 링크는 소모된다. 같은 링크로 답을 바꿀 수 없다.")
    public ApiResponse<Void> respond(@PathVariable String token, @Valid @RequestBody ResponseRequest request) {
        service.respond(token, request.agreed());
        return ApiResponse.success(null);
    }
}
