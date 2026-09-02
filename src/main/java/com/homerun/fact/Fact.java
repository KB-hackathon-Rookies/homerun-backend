package com.homerun.fact;

import java.math.BigDecimal;

/**
 * 판정에 쓸 수 있는 기준 수치 하나.
 *
 * @param code 팩트 코드 (FCT-040 등)
 * @param item 항목 이름
 * @param number 수치. 문자열로만 표현되는 값이면 비어 있다
 * @param unit 수치의 단위. 금액은 전부 원이다
 * @param text 원문 표현. 화면에 그대로 보여줄 수 있다
 * @param sourceUrl 공식 근거 링크
 * @param provisional 값이 바뀔 수 있어 화면에 표시가 필요한가
 */
public record Fact(
        String code, String item, BigDecimal number, String unit, String text, String sourceUrl, boolean provisional) {

    /** 수치가 있는 팩트에서만 부른다. 문자열뿐인 팩트에 부르면 예외다. */
    public BigDecimal requireNumber() {
        if (number == null) {
            throw new IllegalStateException("기준 수치 %s 에는 수치값이 없다: %s".formatted(code, text));
        }
        return number;
    }

    public long requireWon() {
        return requireNumber().longValueExact();
    }
}
