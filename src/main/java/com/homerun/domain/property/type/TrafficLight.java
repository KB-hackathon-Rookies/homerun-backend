package com.homerun.domain.property.type;

/**
 * 매물 카드의 2루 진행 상태(요구사항 명세서 3.6.1 · BR-10).
 *
 * <p>{@link CheckResult} 와 축이 다르다. CheckResult 는 항목 하나의 <b>심각도</b>고, 이것은
 * 매물 하나가 2루 어디까지 왔는지를 나타내는 <b>진행 단계</b>다. 그래서 심각도를 그대로 옮기면
 * 안 된다 — 반환보증 126% 룰 미달은 BLOCK 이지만 RED 가 아니다(BR-11: 대출 판정과 독립).
 */
public enum TrafficLight {
    /** 대출 불가. 위반건축물·신탁등기·임차권등기·압류·소유자 불일치 중 하나라도 해당한다. */
    RED("대출 불가"),
    /** 등기부 확인 필요. 건축물대장은 통과했고 등기부를 아직 못 봤거나 "모르겠어요"가 있다. */
    YELLOW("등기부 확인 필요"),
    /** 은행 상담 가능. 등기부 체크리스트까지 전부 통과했다. */
    GREEN("은행 상담 가능"),
    /** 상담 완료. 은행 사전상담 결과가 입력됐다. */
    BLUE("상담 완료");

    private final String label;

    TrafficLight(String label) {
        this.label = label;
    }

    /** 화면에 그대로 쓸 수 있는 이름. 색만으로 구분하지 않는다(NFR-UX-03). */
    public String label() {
        return label;
    }

    /** 대출 상품 목록을 보여줘도 되는가. RED 면 상품을 아예 노출하지 않는다(FR-P4-02). */
    public boolean showsLoanProducts() {
        return this != RED;
    }
}
