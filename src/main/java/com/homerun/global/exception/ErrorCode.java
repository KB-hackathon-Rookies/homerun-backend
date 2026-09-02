package com.homerun.global.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청값이 올바르지 않습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 본문을 해석할 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_999", "서버 오류가 발생했습니다."),

    MEMBER_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH_001", "존재하지 않는 사용자입니다."),
    ACCESS_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_002", "Access Token이 필요합니다."),
    INVALID_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_003", "유효하지 않은 Access Token입니다."),
    EXPIRED_ACCESS_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_004", "Access Token이 만료되었습니다."),
    REFRESH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTH_005", "Refresh Token이 필요합니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_006", "유효하지 않은 Refresh Token입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_007", "만료되었거나 폐기된 Refresh Token입니다."),
    OAUTH_LOGIN_REJECTED(HttpStatus.UNAUTHORIZED, "AUTH_008", "소셜 로그인이 취소되었거나 거부되었습니다."),
    INVALID_OAUTH_REQUEST(HttpStatus.BAD_REQUEST, "AUTH_009", "유효하지 않은 OAuth 로그인 요청입니다."),
    OAUTH_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "AUTH_010", "소셜 로그인 제공자 연동에 실패했습니다."),
    OAUTH_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_011", "OAuth 설정이 올바르지 않습니다."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "AUTH_012", "접근 권한이 없습니다."),

    CONSENT_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "TERMS_001", "유효하지 않은 동의 토큰입니다."),
    CONSENT_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "TERMS_002", "동의 토큰이 만료되었습니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "TERMS_003", "필수 약관에 동의해야 합니다."),
    TERMS_VERSION_MISMATCH(HttpStatus.CONFLICT, "TERMS_004", "현재 유효한 약관 버전과 일치하지 않습니다."),
    REQUIRED_TERMS_AGREEMENT_REQUIRED(HttpStatus.FORBIDDEN, "TERMS_005", "서비스 이용 전에 필수 약관 동의가 필요합니다."),
    RULE_VERSION_MISMATCH(HttpStatus.CONFLICT, "PLAN_001", "규칙 버전이 변경되었습니다."),
    PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_002", "계획을 찾을 수 없습니다."),
    PLAN_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PLAN_003", "계획에 접근할 권한이 없습니다."),
    PLAN_STEP_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_004", "계획 단계를 찾을 수 없습니다."),
    PLAN_STEP_LOCKED(HttpStatus.CONFLICT, "PLAN_005", "잠긴 단계는 완료할 수 없습니다."),
    INVALID_STAGE_TRANSITION(HttpStatus.CONFLICT, "PLAN_006", "현재 단계에서는 이동할 수 없습니다."),
    PLAN_NOT_ACTIVE(HttpStatus.CONFLICT, "PLAN_007", "진행 중인 계획만 변경할 수 있습니다."),
    PLAN_INPUT_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_008", "저장된 계획 입력을 찾을 수 없습니다."),
    INVALID_UNKNOWN_FIELD(HttpStatus.BAD_REQUEST, "PLAN_009", "모름 처리할 수 없는 입력 필드가 포함되어 있습니다."),
    UNKNOWN_FIELD_HAS_VALUE(HttpStatus.BAD_REQUEST, "PLAN_010", "모름 처리한 필드에는 값을 함께 저장할 수 없습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "COMMON_003", "다른 요청에서 먼저 변경했습니다. 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String message() {
        return message;
    }
}
