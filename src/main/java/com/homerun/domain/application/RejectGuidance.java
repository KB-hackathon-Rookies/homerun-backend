package com.homerun.domain.application;

/**
 * 거절 단계별 다음 행동 안내(APP-01-07).
 *
 * <p>"다른 은행 가보세요"로 끝내면 안 된다. 보증기관에서 막힌 사람에게는 틀린 조언이다.
 */
final class RejectGuidance {

    private RejectGuidance() {}

    static String forStage(RejectStage stage) {
        if (stage == null) {
            return null;
        }
        return switch (stage) {
            case BANK -> "은행 자체 심사에서 막혔다. 다른 은행에 사전상담을 받아보면 결과가 달라질 수 있다.";
            case GUARANTEE -> "보증기관 심사에서 막혔다. 은행을 바꿔도 같은 결과일 가능성이 높다. 집 조건이나 보증금 규모를 다시 봐야 한다.";
            case DOCUMENT -> "서류가 부족해 막혔다. 빠진 서류를 보완하면 같은 상품으로 다시 신청할 수 있다.";
            case PRODUCT -> "상품 조건 자체를 충족하지 못했다. 다른 상품이나 대안 경로를 확인해야 한다.";
        };
    }
}
