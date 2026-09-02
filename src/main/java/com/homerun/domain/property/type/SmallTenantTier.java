package com.homerun.domain.property.type;

/**
 * 소액임차인 최우선변제 지역 구간(FCT-063~066).
 *
 * <p>구간마다 보증금 상한과 변제 한도가 다르다. 팩트 코드를 여기 묶어 두면 규칙이 지역을
 * 몰라도 된다.
 */
public enum SmallTenantTier {
    SEOUL("서울특별시", "FCT-156", "FCT-157"),
    OVERCROWDED("수도권 과밀억제권역·세종·용인·화성·김포", "FCT-158", "FCT-159"),
    METRO("광역시·안산·광주·파주·이천·평택", "FCT-160", "FCT-161"),
    OTHER("그 밖의 지역", "FCT-162", "FCT-163");

    private final String label;
    private final String depositCapFact;
    private final String protectedAmountFact;

    SmallTenantTier(String label, String depositCapFact, String protectedAmountFact) {
        this.label = label;
        this.depositCapFact = depositCapFact;
        this.protectedAmountFact = protectedAmountFact;
    }

    public String label() {
        return label;
    }

    /** 이 금액 이하여야 소액임차인이다. */
    public String depositCapFact() {
        return depositCapFact;
    }

    /** 최우선으로 변제받는 금액. */
    public String protectedAmountFact() {
        return protectedAmountFact;
    }

    /**
     * 법정동코드 앞 두 자리로 구간을 고른다.
     *
     * <p>경기도(41)만 판정하지 않는다. 안산·광주·파주·이천·평택은 광역시 구간이고
     * 용인·화성·김포는 과밀억제 구간이라, 도 코드만으로는 어느 쪽인지 알 수 없다. 틀린
     * 구간으로 판정하면 보호받는 금액을 잘못 알려주게 된다.
     *
     * <p>나머지 도 지역은 조문상 "그 밖의 지역"이 확실하므로 OTHER 로 본다. 알 수 없다고
     * 두면 명백한 지역까지 안내를 못 하게 된다.
     */
    public static SmallTenantTier of(String regionCode) {
        if (regionCode == null || regionCode.length() < 2) {
            return null;
        }
        return switch (regionCode.substring(0, 2)) {
            case "11" -> SEOUL;
            case "36" -> OVERCROWDED;
            case "26", "27", "28", "29", "30", "31" -> METRO;
            // 경기도는 시·군마다 구간이 갈린다. region 시드가 들어오면 정확해진다.
            case "41" -> null;
            case "42", "43", "44", "45", "46", "47", "48", "50", "51", "52" -> OTHER;
            default -> null;
        };
    }
}
