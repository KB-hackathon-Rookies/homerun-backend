package com.homerun.domain.document.type;

/**
 * 방문을 묶는 기준(PLC-01-05).
 *
 * <p>{@code document_issue_method.agency} 는 화면에 그대로 쓰는 자유 문자열이라 묶는 기준이
 * 못 된다. '주민센터'와 '주민센터·구청'과 '세무서·주민센터'는 같은 걸음인데 문자열로는
 * 다르다. 묶는 것은 이 코드로 한다.
 *
 * <p>"가야 하는가"와 "길찾기를 만들 수 있는가"는 다른 물음이다. 재직 회사는 가야 하지만
 * 어디로 갈지는 사람마다 다르다. 본인 보관은 애초에 갈 데가 없다.
 */
public enum IssueAgency {
    /** 집에서 끝난다. */
    ONLINE(false, null),
    /** 이미 가지고 있다. 챙기기만 하면 된다. */
    SELF(false, null),
    /** 가야 하지만 어디로 갈지는 사람마다 다르다. */
    EMPLOYER(true, null),

    COMMUNITY_CENTER(true, "주민센터"),
    CIVIL_KIOSK(true, "무인민원발급기"),
    COURT_KIOSK(true, "법원 무인발급기"),
    REGISTRY_OFFICE(true, "등기소"),
    TAX_OFFICE(true, "세무서");

    private static final String ONLINE_LABEL = "온라인";
    private static final String SELF_LABEL = "본인 보관";
    private static final String EMPLOYER_LABEL = "재직 회사";

    private final boolean visitRequired;
    private final String directionsQuery;

    IssueAgency(boolean visitRequired, String directionsQuery) {
        this.visitRequired = visitRequired;
        this.directionsQuery = directionsQuery;
    }

    public String label() {
        return switch (this) {
            case ONLINE -> ONLINE_LABEL;
            case SELF -> SELF_LABEL;
            case EMPLOYER -> EMPLOYER_LABEL;
            default -> directionsQuery;
        };
    }

    /** 발걸음이 필요한가. 방문 횟수를 셀 때 이것만 센다. */
    public boolean visitRequired() {
        return visitRequired;
    }

    /**
     * 지도 앱으로 안내할 수 있는가.
     *
     * <p>재직 회사는 가야 하지만 여기서는 false 다. 방문 여부와 길찾기 가능 여부를 한
     * 플래그로 묶으면 회사 방문이 목록에서 사라진다.
     */
    public boolean navigable() {
        return directionsQuery != null;
    }

    public String directionsQuery() {
        return directionsQuery;
    }
}
