package com.homerun.domain.terms.type;

/**
 * 약관이 어디까지 필수인가.
 *
 * <p>{@code is_required} 하나로는 두 가지를 구분할 수 없었다. {@code RequiredTermsAgreementFilter}
 * 는 필수 약관 미동의를 <b>모든 요청</b>에서 403 으로 끊는데, 기능별 약관을 필수로 넣으면 그 기능을
 * 쓰지도 않는 사용자까지 앱 전체가 막힌다.
 *
 * <p>DB {@code ck_terms_scope} 제약과 값이 같아야 한다.
 */
public enum TermScope {
    /** 앱을 쓰려면 반드시. 필터가 이것만 본다. */
    SERVICE,
    /** 오픈뱅킹을 쓰려면 반드시. 안 쓰는 사용자는 동의하지 않아도 앱이 막히지 않는다. */
    OPEN_BANKING
}
