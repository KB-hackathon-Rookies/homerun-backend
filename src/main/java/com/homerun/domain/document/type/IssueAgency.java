package com.homerun.domain.document.type;

/**
 * 방문을 묶는 기준(PLC-01-05).
 *
 * <p>{@code document_issue_method.agency} 는 화면에 그대로 쓰는 자유 문자열이라 묶는 기준이
 * 못 된다. '주민센터'와 '주민센터·구청'과 '세무서·주민센터'는 같은 걸음인데 문자열로는
 * 다르다. 묶는 것은 이 코드로 한다.
 */
public enum IssueAgency {
    ONLINE("온라인", false),
    COMMUNITY_CENTER("주민센터", true),
    CIVIL_KIOSK("무인민원발급기", true),
    COURT_KIOSK("법원 무인발급기", true),
    REGISTRY_OFFICE("등기소", true),
    TAX_OFFICE("세무서", true),
    /** 재직 회사. 어디로 갈지는 사람마다 달라 길찾기를 만들 수 없다. */
    EMPLOYER("재직 회사", false),
    /** 이미 가지고 있는 것. 어디로도 가지 않는다. */
    SELF("본인 보관", false);

    private final String label;
    private final String directionsQuery;

    IssueAgency(String label, boolean searchable) {
        this.label = label;
        this.directionsQuery = searchable ? label : null;
    }

    public String label() {
        return label;
    }

    /** 갈 곳이 정해진 유형인가. 회사나 본인 보관은 길찾기를 만들지 않는다. */
    public boolean navigable() {
        return directionsQuery != null;
    }

    /**
     * 지도 앱 검색 링크(PLC-01-04).
     *
     * <p>좌표를 모르므로 목적지를 찍어 주지는 못하고 이름으로 검색해 준다. 정확한 지점은
     * 주변 기관 검색(PLC-01-01)이 붙어야 나온다.
     */
    public String directionsQuery() {
        return directionsQuery;
    }
}
