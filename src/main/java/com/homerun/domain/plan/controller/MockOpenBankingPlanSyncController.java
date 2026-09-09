package com.homerun.domain.plan.controller;

import com.homerun.domain.openbanking.dto.request.MockConnectRequest;
import com.homerun.domain.openbanking.dto.response.FinancialSnapshotResponse;
import com.homerun.domain.plan.service.OpenBankingPlanSyncService;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 전용 가짜 오픈뱅킹 연동.
 *
 * <p>{@link OpenBankingPlanSyncController} 에서 떼어낸 이유가 전부다. 한 클래스에 같이 있으면
 * 조건을 걸 수 없다 — 클래스에 걸면 <b>진짜</b> 동기화까지 같이 사라지기 때문이다. 진짜 동기화는
 * 모든 환경에 있어야 하고 이 경로는 데모에만 있어야 하므로, 조건을 걸 수 있는 최소 단위인
 * 컨트롤러 클래스를 따로 둔다.
 *
 * <p>이 경로는 선택한 페르소나의 소득·순자산을 계획 입력에 <b>확정 상태</b>
 * ({@code financialDataConfirmed=true})로 적재한다. 확인된 값은 정책 판정에서 NEED_INFO 로
 * 빠지지 않고 곧바로 PASS/FAIL 비교에 들어간다. 열려 있으면 로그인한 누구나 오픈뱅킹 연결도
 * 실제 거래내역도 없이 판정 근거를 지어낼 수 있다는 뜻이다.
 *
 * <p>그래서 데이터 모킹과 <b>같은 플래그</b>({@code external-api.open-banking.mock-data},
 * env {@code OPEN_BANKING_MOCK_DATA})에 묶는다. 기본값이 false 이므로 운영·CI 에서는 빈이 아예
 * 올라오지 않고 경로도 없다(404). 데모는 플래그를 켜고 뜨므로 그대로 쓸 수 있다. 사업자 등록이
 * 끝나면 {@code MockDataOpenBankingClient} 와 함께 이 클래스도 지운다.
 *
 * <p>경로는 분리 전과 같다. 데모·프론트가 이미 이 주소를 부르고 있어 바꾸면 안 된다.
 */
@RestController
@RequestMapping("/api/v1/plans/{planId}/input/open-banking-sync/mock")
@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")
@Tag(name = "계획 입력", description = "진단 입력 자동 저장과 외부 금융정보 동기화")
public class MockOpenBankingPlanSyncController {

    private final OpenBankingPlanSyncService service;

    public MockOpenBankingPlanSyncController(OpenBankingPlanSyncService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(
            summary = "데모용 가짜 오픈뱅킹 연동",
            description = "데모 전용이다. 실연동 불가로, 선택한 페르소나의 소득·자산을 스냅샷과 계획 입력에 확정 상태로 적재한다. "
                    + "연동 연출 지연은 프론트에서 처리하며 이 API 는 즉시 응답한다. "
                    + "`external-api.open-banking.mock-data=true` 일 때만 존재하며 운영에서는 404 다.")
    public ApiResponse<FinancialSnapshotResponse> mock(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable Long planId,
            @Valid @RequestBody MockConnectRequest request) {
        return ApiResponse.success(service.mockConnect(principal.memberId(), planId, request.persona()));
    }
}
