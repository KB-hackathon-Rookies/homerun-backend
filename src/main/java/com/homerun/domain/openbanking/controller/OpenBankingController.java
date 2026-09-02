package com.homerun.domain.openbanking.controller;

import com.homerun.domain.openbanking.dto.response.OpenBankingAccountResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingBalanceResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingConnectionResponse;
import com.homerun.domain.openbanking.dto.response.OpenBankingTransactionPageResponse;
import com.homerun.domain.openbanking.service.OpenBankingService;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/open-banking")
@Tag(name = "오픈뱅킹", description = "금융결제원 OAuth 연결 및 본인 계좌·잔액·거래내역 조회")
public class OpenBankingController {

    private static final String STATE_KEY = "open-banking.oauth.state";
    private static final String MEMBER_KEY = "open-banking.oauth.member-id";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OpenBankingService service;

    public OpenBankingController(OpenBankingService service) {
        this.service = service;
    }

    @GetMapping("/connect")
    @Operation(summary = "오픈뱅킹 연결 시작", description = "금융결제원 계좌등록·동의 화면으로 이동합니다.")
    public ResponseEntity<Void> connect(@AuthenticationPrincipal MemberPrincipal principal, HttpSession session) {
        String state = createState();
        session.setAttribute(STATE_KEY, state);
        session.setAttribute(MEMBER_KEY, principal.memberId());
        URI authorizationUri = service.authorizationUri(state);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(authorizationUri)
                .build();
    }

    @GetMapping("/callback")
    @Operation(summary = "오픈뱅킹 연결 콜백", description = "금융결제원이 호출하며 Access Token을 암호화해 저장합니다.")
    public ApiResponse<OpenBankingConnectionResponse> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            HttpSession session) {
        if (error != null && !error.isBlank()) {
            clearOAuthSession(session);
            throw new BusinessException(ErrorCode.OPEN_BANKING_AUTH_REJECTED);
        }
        String expectedState = (String) session.getAttribute(STATE_KEY);
        Long memberId = (Long) session.getAttribute(MEMBER_KEY);
        clearOAuthSession(session);
        if (isBlank(code)
                || isBlank(state)
                || expectedState == null
                || !expectedState.equals(state)
                || memberId == null) {
            throw new BusinessException(ErrorCode.INVALID_OPEN_BANKING_REQUEST);
        }
        return ApiResponse.success(service.connect(memberId, code));
    }

    @GetMapping("/connection")
    @Operation(summary = "오픈뱅킹 연결 상태 조회")
    public ApiResponse<OpenBankingConnectionResponse> connection(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(service.connection(principal.memberId()));
    }

    @GetMapping("/accounts")
    @Operation(summary = "연결 계좌 목록 조회")
    public ApiResponse<List<OpenBankingAccountResponse>> accounts(@AuthenticationPrincipal MemberPrincipal principal) {
        return ApiResponse.success(service.accounts(principal.memberId()));
    }

    @GetMapping("/accounts/{fintechUseNumber}/balance")
    @Operation(summary = "계좌 잔액 조회")
    public ApiResponse<OpenBankingBalanceResponse> balance(
            @AuthenticationPrincipal MemberPrincipal principal,
            @Parameter(description = "금융결제원이 발급한 24자리 핀테크이용번호") @PathVariable @Size(min = 24, max = 24)
                    String fintechUseNumber) {
        return ApiResponse.success(service.balance(principal.memberId(), fintechUseNumber));
    }

    @GetMapping("/accounts/{fintechUseNumber}/transactions")
    @Operation(summary = "계좌 거래내역 조회", description = "한 페이지 최대 25건이며 nextTraceInfo로 다음 페이지를 조회합니다.")
    public ApiResponse<OpenBankingTransactionPageResponse> transactions(
            @AuthenticationPrincipal MemberPrincipal principal,
            @PathVariable @Size(min = 24, max = 24) String fintechUseNumber,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) @Size(max = 40) String nextTraceInfo) {
        return ApiResponse.success(
                service.transactions(principal.memberId(), fintechUseNumber, fromDate, toDate, nextTraceInfo));
    }

    private String createState() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void clearOAuthSession(HttpSession session) {
        session.removeAttribute(STATE_KEY);
        session.removeAttribute(MEMBER_KEY);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
