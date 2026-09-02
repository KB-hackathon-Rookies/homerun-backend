package com.homerun.application;

/**
 * 어디서 막혔는가(APP-01-07).
 *
 * <p>전세·월세 대출은 은행 심사와 보증기관 심사를 모두 통과해야 한다. 어느 쪽에서 막혔는지에
 * 따라 사용자가 할 일이 완전히 달라진다. 은행이면 다른 은행을 가보면 되지만, 보증기관이면
 * 집이나 소득 조건 자체를 바꿔야 한다.
 */
public enum RejectStage {
    /** 은행 자체 심사. 다른 은행에서 될 수도 있다. */
    BANK("은행 심사"),
    /** 보증기관 심사. 은행을 바꿔도 같은 결과일 가능성이 높다. */
    GUARANTEE("보증기관 심사"),
    /** 서류 미비. 보완하면 된다. */
    DOCUMENT("서류"),
    /** 상품 조건 자체를 못 맞춤. 다른 상품을 봐야 한다. */
    PRODUCT("상품 조건");

    private final String label;

    RejectStage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
