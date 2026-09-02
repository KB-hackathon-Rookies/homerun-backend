package com.homerun.domain.consent.type;

/** 동의 링크의 상태. */
public enum ConsentStatus {
    /** 아직 응답이 없고 만료 전이다. */
    PENDING,
    /** 가구원이 동의 또는 거절로 응답했다. */
    RESPONDED,
    /** 유효기간이 지났다. 재발급이 필요하다. */
    EXPIRED,
    /** 재발급 등으로 폐기됐다. */
    REVOKED
}
