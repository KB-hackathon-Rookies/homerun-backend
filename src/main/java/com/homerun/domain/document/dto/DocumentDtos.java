package com.homerun.domain.document.dto;

import com.homerun.domain.document.type.IssueMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 서류 발급 안내(ISS-01) 응답. */
public final class DocumentDtos {

    private DocumentDtos() {}

    /**
     * 발급방법 하나(ISS-01-01).
     *
     * @param url 공식 발급 페이지. 방문만 되는 서류는 없다(ISS-01-03)
     * @param fee 이 방법의 기본 수수료(원). 같은 서류도 방법마다 다르다
     * @param feeNote 수수료가 조건에 따라 갈릴 때의 설명. 단일 금액으로 못 적는 경우다
     * @param requirements 챙길 것. 온라인은 대체로 없다(ISS-01-04)
     * @param note 방문 전에 알아야 할 것 — 운영시간, 처리 조건(ISS-01-05)
     * @param recommended 이 서류에서 먼저 권하는 방법인가(ISS-01-02)
     */
    @Schema(description = "서류 발급방법")
    public record IssueMethodView(
            IssueMethod method,
            String label,
            String agency,
            String url,
            int fee,
            String feeNote,
            String requirements,
            String note,
            boolean recommended) {}

    /**
     * 서류 목록의 한 줄.
     *
     * @param cheapestFee 가장 싼 방법의 수수료(원)
     */
    @Schema(description = "서류 요약")
    public record DocumentSummary(
            String code, String name, String issuer, boolean onlineAvailable, int cheapestFee, Integer validityDays) {}

    /**
     * 서류 하나의 발급 안내 전체(ISS-01).
     *
     * @param methods 권하는 순서대로. 온라인이 있으면 앞에 온다
     * @param onlineOnlyBlocked 온라인으로는 못 떼는 서류인가. 전입세대확인서가 그렇다
     * @param validityDays 발급일로부터 며칠까지 인정되는가. 근거가 없으면 비어 있다
     * @param validityNote 인정 기간을 모를 때의 안내. 아는 경우에는 null 이다
     */
    @Schema(description = "서류 발급 안내")
    public record DocumentIssueGuide(
            String code,
            String name,
            String issuer,
            Integer validityDays,
            String validityNote,
            String note,
            boolean onlineOnlyBlocked,
            List<IssueMethodView> methods) {

        public DocumentIssueGuide {
            methods = List.copyOf(methods);
        }
    }

    @Schema(description = "서류 목록")
    public record DocumentList(List<DocumentSummary> documents) {

        public DocumentList {
            documents = List.copyOf(documents);
        }
    }
}
