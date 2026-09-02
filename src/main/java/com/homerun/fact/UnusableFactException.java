package com.homerun.fact;

/**
 * 확정되지 않은 수치로 판정을 시도했을 때 던진다.
 *
 * <p>이 예외를 잡아서 임의의 기본값으로 넘어가면 안 된다. 정책 판정은 "추가확인" 상태로
 * 남겨야 한다(NFR-01-06).
 */
public class UnusableFactException extends RuntimeException {

    private final String factCode;
    private final Confidence confidence;

    public UnusableFactException(String factCode, Confidence confidence) {
        super("기준 수치 %s 는 확정도가 %s 라 판정에 쓸 수 없다".formatted(factCode, confidence));
        this.factCode = factCode;
        this.confidence = confidence;
    }

    public String factCode() {
        return factCode;
    }

    public Confidence confidence() {
        return confidence;
    }
}
