package com.homerun.consent;

import com.homerun.consent.ConsentDtos.ConsentOverview;
import com.homerun.consent.ConsentDtos.ConsentView;
import com.homerun.consent.ConsentDtos.IssueRequest;
import com.homerun.consent.ConsentDtos.IssueResponse;
import com.homerun.consent.ConsentDtos.ResponseRequest;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가구원 확인·동의(FAM-01).
 *
 * <p>planId 를 파라미터로 받는다. 세션에서 꺼내려면 인증 필터가 있어야 하는데 아직 없다.
 * SEC-01-04 접근 권한 분리가 들어오면 소유권 검증을 함께 붙인다.
 */
@RestController
@RequestMapping("/api/v1/consents")
@Tag(name = "가구원 동의", description = "일회용 동의 링크 발급과 응답 추적")
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "동의 현황 조회", description = "가구원별 확인 상태를 준다. allVerified 가 false 면 자격을 확정하지 말고 추가확인으로 남겨야 한다.")
    public ConsentOverview overview(@RequestParam Long planId) {
        return service.overview(planId);
    }

    @PostMapping
    @Operation(summary = "일회용 동의 링크 발급", description = "원문 토큰은 이 응답에서만 볼 수 있다. 서버는 해시만 저장하므로 다시 조회할 수 없다.")
    public ResponseEntity<IssueResponse> issue(@Valid @RequestBody IssueRequest request) {
        // 링크가 담긴 응답이 중계 서버나 브라우저 히스토리에 남지 않게 한다.
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(service.issue(request));
    }

    @GetMapping("/{token}")
    @Operation(summary = "가구원 동의 화면", description = "관계와 목적만 준다. 이름과 주민식별정보는 담지 않는다.")
    public ResponseEntity<ConsentView> view(@PathVariable String token) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(service.view(token));
    }

    @PostMapping("/{token}/response")
    @Operation(summary = "가구원 동의·거절 제출", description = "응답하면 링크는 소모된다. 같은 링크로 답을 바꿀 수 없다.")
    public ResponseEntity<Void> respond(@PathVariable String token, @Valid @RequestBody ResponseRequest request) {
        service.respond(token, request.agreed());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{consentId}/reissue")
    @Operation(summary = "동의 링크 재발급", description = "이전 링크는 폐기된다. 유효한 링크가 둘이 되지 않게 한다.")
    public ResponseEntity<IssueResponse> reissue(@PathVariable Long consentId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .body(service.reissue(consentId));
    }
}
