package com.homerun.domain.openbanking.controller;

import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 전용 가짜 오픈뱅킹 연결.
 *
 * <p>{@link OpenBankingController} 의 {@code /connect}·{@code /callback} 은 금융결제원 인가
 * 페이지를 거친다(팝업 → 코드 → 토큰 교환). 사업자 등록 전 데모에서는 그 페이지를 쓸 수 없어
 * 연동 화면이 팝업·폴링에 걸려 있다. 이 경로는 인가 없이 연결 레코드만 세워, 이후 계좌·잔액·
 * 요약이 {@code MockDataOpenBankingClient} 샘플 데이터로 답하게 한다.
 *
 * <p>데이터 모킹과 <b>같은 플래그</b>({@code external-api.open-banking.mock-data}, env
 * {@code OPEN_BANKING_MOCK_DATA})에 묶는다 — 기본값 false 이므로 운영·CI 에서는 빈이 올라오지
 * 않고 경로도 없다(404). 실연동을 붙이면 {@code MockDataOpenBankingClient} 와 함께 지운다.
 */
@RestController
@RequestMapping("/api/v1/open-banking/mock-connect")
@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")
@Tag(name = "오픈뱅킹", description = "오픈뱅킹 연동과 금융정보 조회")
public class MockOpenBankingController {

    private final OpenBankingService service;

    public MockOpenBankingController(OpenBankingService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "데모용 가짜 오픈뱅킹 연결",
            description = "데모 전용이다. 금융결제원 인가 없이 연결 레코드를 만들어 이후 계좌·요약이 샘플 데이터로 답하게 한다. "
                    + "연동 연출 지연은 프론트에서 처리하며 이 API 는 즉시 응답한다. "
                    + "`external-api.open-banking.mock-data=true` 일 때만 존재하며 운영에서는 404 다.")
    public ApiResponse<OpenBankingConnectionResponse> mockConnect(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(service.mockConnect(principal.memberId()));
    }
}
