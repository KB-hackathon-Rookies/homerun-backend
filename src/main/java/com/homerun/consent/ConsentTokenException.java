package com.homerun.consent;

/** 동의 링크를 쓸 수 없을 때. 사유는 사용자에게 그대로 보여줘도 되는 수준으로만 담는다. */
public class ConsentTokenException extends RuntimeException {

    private final String code;

    ConsentTokenException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }

    static ConsentTokenException invalid() {
        // 존재하지 않는 토큰과 폐기된 토큰을 구분해 알려주지 않는다. 링크를 넣어 보며
        // 유효한 값을 찾아내는 시도에 힌트를 주게 된다.
        return new ConsentTokenException("CONSENT_TOKEN_INVALID", "유효하지 않은 동의 링크다");
    }

    static ConsentTokenException expired() {
        return new ConsentTokenException("CONSENT_TOKEN_EXPIRED", "동의 링크의 유효기간이 지났다. 재발급이 필요하다");
    }
}
