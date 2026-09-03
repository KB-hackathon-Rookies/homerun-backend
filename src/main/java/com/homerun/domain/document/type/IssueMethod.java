package com.homerun.domain.document.type;

/** 서류 발급방법(ISS-01-01). document_issue_method.ck_issue_method 와 값이 같아야 한다. */
public enum IssueMethod {
    /** 공식 사이트에서 뗀다. 대체로 무료거나 가장 싸다. */
    ONLINE("온라인"),
    /** 무인민원발급기. 24시간 되는 곳이 있다. */
    KIOSK("무인민원발급기"),
    /** 기관을 직접 찾아간다. */
    VISIT("방문");

    private final String label;

    IssueMethod(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
