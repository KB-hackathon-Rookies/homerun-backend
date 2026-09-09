package com.homerun.global.exception;

import org.springframework.http.HttpStatus;

/**
 * API 응답에 실어 보내는 에러 코드.
 *
 * <p><b>새 코드는 자기 도메인 블록 안에 넣는다.</b> 블록은 빈 줄로 나뉜다. 아무 데나 맨 뒤에
 * 붙이면 다른 사람과 같은 줄을 고치게 되어 브랜치마다 충돌한다.
 *
 * <p>마지막 상수 뒤에도 쉼표를 두고 {@code ;} 는 자기 줄에 둔다. 이러면 코드를 더할 때
 * 기존 줄을 하나도 건드리지 않으므로, 두 사람이 각자 다른 블록에 추가해도 자동으로 합쳐진다.
 */
public enum ErrorCode {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "COMMON_001", "요청값이 올바르지 않습니다."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_002", "요청 본문을 해석할 수 없습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "COMMON_003", "다른 요청에서 먼저 변경했습니다. 다시 시도해 주세요."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_004", "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "COMMON_005", "지원하지 않는 Content-Type 입니다."),
    ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_006", "존재하지 않는 API 경로입니다."),
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
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_013", "이미 가입된 이메일입니다."),
    EMAIL_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_014", "이메일 인증번호가 만료되었습니다."),
    EMAIL_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "AUTH_015", "이메일 인증번호가 올바르지 않습니다."),
    EMAIL_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_016", "이메일 인증 시도 횟수를 초과했습니다."),
    EMAIL_VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_017", "잠시 후 이메일 인증을 다시 요청해 주세요."),
    EMAIL_VERIFICATION_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_018", "이메일 인증이 만료되었거나 유효하지 않습니다."),
    EMAIL_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_019", "인증 이메일 발송에 실패했습니다."),
    EMAIL_AUTH_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_020", "이메일 인증 설정이 올바르지 않습니다."),
    EMAIL_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_021", "이메일 인증 서비스를 사용할 수 없습니다."),
    INVALID_EMAIL_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AUTH_022", "이메일 또는 비밀번호가 올바르지 않습니다."),
    MEMBER_WITHDRAWN(HttpStatus.UNAUTHORIZED, "AUTH_023", "탈퇴한 사용자입니다."),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "AUTH_024", "이미 가입된 휴대전화 번호입니다."),
    PHONE_VERIFICATION_EXPIRED(HttpStatus.BAD_REQUEST, "AUTH_025", "휴대전화 인증번호가 만료되었습니다."),
    PHONE_VERIFICATION_INVALID(HttpStatus.BAD_REQUEST, "AUTH_026", "휴대전화 인증번호가 올바르지 않습니다."),
    PHONE_VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_027", "휴대전화 인증 시도 횟수를 초과했습니다."),
    PHONE_VERIFICATION_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "AUTH_028", "잠시 후 휴대전화 인증을 다시 요청해 주세요."),
    PHONE_VERIFICATION_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH_029", "휴대전화 인증이 만료되었거나 유효하지 않습니다."),
    SMS_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "AUTH_030", "인증 문자 발송에 실패했습니다."),
    PHONE_AUTH_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AUTH_031", "휴대전화 인증 설정이 올바르지 않습니다."),
    PHONE_AUTH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_032", "휴대전화 인증 서비스를 사용할 수 없습니다."),
    SIGNUP_REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "AUTH_033", "회원가입 필수 정보가 누락되었습니다."),
    SOCIAL_SIGNUP_ALREADY_COMPLETED(HttpStatus.CONFLICT, "AUTH_034", "이미 회원가입이 완료된 계정입니다."),

    OPEN_BANKING_NOT_CONNECTED(HttpStatus.NOT_FOUND, "OPENBANKING_001", "연결된 오픈뱅킹 정보가 없습니다."),
    OPEN_BANKING_CONFIGURATION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "OPENBANKING_002", "오픈뱅킹 설정이 올바르지 않습니다."),
    OPEN_BANKING_AUTH_REJECTED(HttpStatus.UNAUTHORIZED, "OPENBANKING_003", "오픈뱅킹 연결이 취소되었거나 거부되었습니다."),
    INVALID_OPEN_BANKING_REQUEST(HttpStatus.BAD_REQUEST, "OPENBANKING_004", "유효하지 않은 오픈뱅킹 연결 요청입니다."),
    OPEN_BANKING_PROVIDER_ERROR(HttpStatus.BAD_GATEWAY, "OPENBANKING_005", "오픈뱅킹 연동에 실패했습니다."),
    OPEN_BANKING_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "OPENBANKING_006", "연결된 계좌를 찾을 수 없습니다."),
    INVALID_TRANSACTION_PERIOD(HttpStatus.BAD_REQUEST, "OPENBANKING_007", "거래내역 조회 기간이 올바르지 않습니다."),
    INVALID_OPEN_BANKING_BANK_CODE(HttpStatus.BAD_REQUEST, "OPENBANKING_008", "금융기관 코드는 숫자 3자리여야 합니다."),
    OPEN_BANKING_PAGINATION_ERROR(HttpStatus.BAD_GATEWAY, "OPENBANKING_009", "오픈뱅킹 페이지 응답이 올바르지 않습니다."),
    OPEN_BANKING_FINANCIAL_SNAPSHOT_NOT_FOUND(HttpStatus.NOT_FOUND, "OPENBANKING_010", "저장된 금융정보 스냅샷이 없습니다."),

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
    PLAN_STAGE_LOCKED(HttpStatus.CONFLICT, "PLAN_011", "아직 진입할 수 없는 계획 단계입니다."),
    PLAN_REGION_NOT_FOUND(HttpStatus.BAD_REQUEST, "PLAN_012", "존재하지 않는 지역입니다."),
    PLAN_REQUIRED_INPUT_MISSING(HttpStatus.BAD_REQUEST, "PLAN_013", "단계 완료에 필요한 입력을 확인해 주세요."),
    PLAN_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_014", "계획 할 일을 찾을 수 없습니다."),
    INVALID_TASK_STATUS_TRANSITION(HttpStatus.CONFLICT, "PLAN_015", "변경할 수 없는 할 일 상태입니다."),
    PLAN_TASK_SKIP_NOT_ALLOWED(HttpStatus.CONFLICT, "PLAN_016", "건너뛸 수 없는 할 일입니다."),
    INVALID_FINANCIAL_INCOME_CONFIRMATION(HttpStatus.CONFLICT, "PLAN_017", "확인할 수 있는 금융 소득 정보가 없습니다."),
    PLAN_INPUT_REVISION_MISMATCH(HttpStatus.CONFLICT, "PLAN_018", "다른 곳에서 입력이 변경되었습니다. 최신 입력을 다시 불러와 주세요."),
    PLAN_INPUT_STEP_INVALID(HttpStatus.BAD_REQUEST, "PLAN_019", "현재 STEP에서 저장할 수 없는 입력이 포함되어 있습니다."),
    PLAN_INPUT_STEP_INCOMPLETE(HttpStatus.BAD_REQUEST, "PLAN_020", "현재 STEP의 답변을 입력하거나 모름으로 표시해 주세요."),
    FIRST_BASE_REVIEW_REQUIRED(HttpStatus.CONFLICT, "PLAN_021", "최종 확인 STEP을 저장한 후 1루를 제출해 주세요."),
    PLAN_ACTIVE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAN_022", "진행 중인 계획이 없습니다."),

    DIAGNOSIS_INPUT_REQUIRED(HttpStatus.BAD_REQUEST, "DIA_001", "진단 계산에 필요한 계획 입력을 확인해 주세요."),
    DIAGNOSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "DIA_002", "저장된 진단 결과가 없습니다."),
    DIAGNOSIS_CALCULATION_OVERFLOW(HttpStatus.BAD_REQUEST, "DIA_003", "진단 계산 금액이 허용 범위를 벗어났습니다."),
    DIAGNOSIS_JEONSE_PLAN_REQUIRED(HttpStatus.BAD_REQUEST, "DIA_004", "전세 또는 반전세 계획에서만 1루 진단을 이용할 수 있습니다."),
    FIRST_BASE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "DIA_005", "완료된 1루 진단 결과가 없습니다."),

    POLICY_JEONSE_PLAN_REQUIRED(HttpStatus.BAD_REQUEST, "POLICY_001", "전세 또는 반전세 계획에서만 정책 판정을 이용할 수 있습니다."),

    HOUSEHOLD_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "FAM_001", "가구원을 찾을 수 없습니다."),
    CONSENT_NOT_FOUND(HttpStatus.NOT_FOUND, "FAM_002", "동의 건을 찾을 수 없습니다."),
    CONSENT_ALREADY_RESPONDED(HttpStatus.CONFLICT, "FAM_003", "이미 응답이 끝난 동의 건입니다."),

    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "APP_001", "신청 건을 찾을 수 없습니다."),
    APPLICATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "APP_002", "이미 신청한 정책입니다."),
    REJECT_STAGE_REQUIRED(HttpStatus.BAD_REQUEST, "APP_003", "거절은 막힌 단계를 함께 기록해야 합니다."),
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, "APP_004", "존재하지 않는 정책입니다."),

    CONTRACT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRP_001", "계약 정보를 찾을 수 없습니다."),
    LEASE_END_NOT_FOUND(HttpStatus.NOT_FOUND, "PRP_020", "저장된 갱신·퇴거 결정이 없습니다."),
    PROPERTY_NOT_IN_PLAN(HttpStatus.BAD_REQUEST, "PRP_002", "이 계획의 매물이 아닙니다."),
    PROPERTY_COMPARISON_DUPLICATE(HttpStatus.BAD_REQUEST, "PRP_004", "같은 매물을 중복해서 비교할 수 없습니다."),
    PROPERTY_LIMIT_EXCEEDED(HttpStatus.BAD_REQUEST, "PRP_008", "매물은 최대 5개까지 등록할 수 있습니다."),
    PROPERTY_DELETE_LOCKED(HttpStatus.CONFLICT, "PRP_021", "최종 선택 또는 계약에 사용한 매물은 삭제할 수 없습니다."),
    PROPERTY_RECHECK_LOCKED(HttpStatus.CONFLICT, "PRP_022", "최종 선택 또는 계약에 사용한 매물은 재진단할 수 없습니다."),
    PROPERTY_WORKFLOW_REVISION_MISMATCH(HttpStatus.CONFLICT, "PRP_009", "다른 요청에서 매물 확인 단계가 변경되었습니다."),
    PROPERTY_WORKFLOW_STEP_INVALID(HttpStatus.CONFLICT, "PRP_010", "현재 매물 확인 단계에서는 저장할 수 없습니다."),
    PROPERTY_LOAN_PRODUCTS_NOT_READY(HttpStatus.CONFLICT, "PRP_011", "필수 매물 확인을 마친 후 대출 상품을 판정할 수 있습니다."),
    PROPERTY_CONSULTATION_NOT_READY(HttpStatus.CONFLICT, "PRP_012", "GREEN 상태의 매물만 은행 상담 결과를 저장할 수 있습니다."),
    BANK_CONSULTATION_NOT_SELECTABLE(HttpStatus.CONFLICT, "PRP_013", "대출 가능 답변을 받은 상담 결과만 최종 선택할 수 있습니다."),
    SECOND_BASE_DECISION_REVISION_MISMATCH(HttpStatus.CONFLICT, "PRP_014", "다른 요청에서 최종 매물과 대출 조건이 변경되었습니다."),
    SECOND_BASE_FINAL_TERMS_INCOMPLETE(HttpStatus.CONFLICT, "PRP_015", "상품·담보·한도·금리를 확인한 상담 결과를 최종 선택해 주세요."),
    SECOND_BASE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRP_016", "완료된 2루 결과가 없습니다."),
    BANK_CONSULTATION_NOT_FOUND(HttpStatus.NOT_FOUND, "PRP_005", "은행 상담 결과를 찾을 수 없습니다."),
    CONSULTATION_PROPERTY_MISMATCH(HttpStatus.BAD_REQUEST, "PRP_006", "선택한 상담 결과가 해당 매물의 것이 아닙니다."),
    PROPERTY_DECISION_NOT_FOUND(HttpStatus.NOT_FOUND, "PRP_007", "확정한 매물과 대출 조건이 없습니다."),
    THIRD_BASE_EXECUTION_INCOMPLETE(HttpStatus.CONFLICT, "PRP_017", "잔금 지급과 전입신고를 완료한 뒤 3루를 마칠 수 있습니다."),
    THIRD_BASE_REGISTRY_RECHECK_REQUIRED(HttpStatus.CONFLICT, "PRP_018", "잔금일 등기부 대조가 안전 상태여야 3루를 마칠 수 있습니다."),

    DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ISS_001", "존재하지 않는 서류입니다."),
    DOCUMENT_STATUS_NOT_SELECTABLE(HttpStatus.BAD_REQUEST, "ISS_002", "직접 지정할 수 없는 서류 상태입니다."),

    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTI_001", "알림을 찾을 수 없습니다."),

    LOAN_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "RF_001", "등록된 실행 대출이 없습니다."),
    RETURN_GUARANTEE_NOT_FOUND(HttpStatus.NOT_FOUND, "RF_004", "저장된 반환보증 가입 상태가 없습니다."),
    LOAN_BALANCE_REQUIRED(HttpStatus.BAD_REQUEST, "RF_003", "원리금균등상환은 대출 잔액을 직접 입력해야 합니다."),
    FIXED_EXPENSE_NOT_FOUND(HttpStatus.NOT_FOUND, "RF_002", "고정지출을 찾을 수 없습니다."),

    EDUCATION_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "EDU_001", "교육 콘텐츠를 찾을 수 없습니다."),
    EDUCATION_QUIZ_SUBMISSION_INVALID(HttpStatus.BAD_REQUEST, "EDU_002", "퀴즈 제출이 올바르지 않습니다."),

    AI_COACH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "COACH_001", "AI 코치 서비스를 사용할 수 없습니다."),
    AI_COACH_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "COACH_002", "AI 코치 응답이 지연되고 있습니다. 잠시 후 다시 시도해 주세요."),
    AI_COACH_UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY, "COACH_003", "AI 코치 답변을 받지 못했습니다."),
    ;

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
