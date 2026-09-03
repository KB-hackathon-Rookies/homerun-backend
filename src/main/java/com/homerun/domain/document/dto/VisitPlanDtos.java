package com.homerun.domain.document.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 발급처별 방문 계획(PLC-01-04 · PLC-01-05 · PLC-01-06) 요청·응답. */
public final class VisitPlanDtos {

    /** 한 번에 계획할 수 있는 서류 수. 카탈로그가 열 종류라 넉넉하고, 조회 폭주를 막는다. */
    public static final int MAX_DOCUMENTS = 30;

    private VisitPlanDtos() {}

    /**
     * 방문 계획 요청.
     *
     * @param documentCodes 준비해야 할 서류 코드
     * @param preferOnline 온라인으로 되는 것은 온라인으로 볼지 여부. 기본은 그렇게 본다.
     *     공동인증서나 프린터가 없거나 제출처가 관인 찍힌 원본을 요구하면 false 로 보낸다.
     *     그때는 전부 방문으로 잡아 한 번에 떼도록 묶는다
     */
    @Schema(description = "방문 계획 요청")
    public record VisitPlanRequest(
            @NotEmpty(message = "서류를 하나 이상 지정해야 한다")
            @Size(max = MAX_DOCUMENTS, message = "서류는 한 번에 " + MAX_DOCUMENTS + "건까지 지정할 수 있다")
            List<@NotBlank(message = "서류 코드가 비어 있다") String> documentCodes,

            Boolean preferOnline) {

        public VisitPlanRequest {
            documentCodes = documentCodes == null ? List.of() : List.copyOf(documentCodes);
        }

        public boolean onlineFirst() {
            return preferOnline == null || preferOnline;
        }
    }

    /**
     * 한 번의 방문으로 뗄 서류 하나(PLC-01-06).
     *
     * @param feeNote 수수료가 조건에 따라 갈릴 때의 설명
     */
    @Schema(description = "방문해서 뗄 서류")
    public record VisitTask(String documentCode, String documentName, int fee, String feeNote, String requirements) {}

    /**
     * 한 곳으로 묶인 방문(PLC-01-05).
     *
     * @param checklist 이 방문에서 챙길 것. 서류마다 겹치는 준비물은 하나로 합친다
     * @param totalFee 이 방문에서 낼 수수료 합계(원)
     * @param directionsUrl 지도 앱 길찾기 링크(PLC-01-04)
     * @param notes 방문 전에 알아야 할 것
     */
    @Schema(description = "한 기관 방문")
    public record AgencyVisit(
            String agency,
            List<VisitTask> tasks,
            List<String> checklist,
            int totalFee,
            String directionsUrl,
            List<String> notes) {

        public AgencyVisit {
            tasks = List.copyOf(tasks);
            checklist = List.copyOf(checklist);
            notes = List.copyOf(notes);
        }
    }

    /**
     * 방문 계획 전체.
     *
     * @param onlineDocuments 집에서 끝나는 서류. 나갈 필요가 없다
     * @param alreadyHeldDocuments 이미 가지고 있는 것. 챙기기만 하면 된다
     * @param visits 가야 할 곳. 넣은 순서를 지킨다
     * @param unknownCodes 카탈로그에 없는 코드. 조용히 버리지 않는다
     * @param totalFee 방문으로 나가는 수수료 합계(원)
     */
    @Schema(description = "발급처별 방문 계획")
    public record VisitPlan(
            List<VisitTask> onlineDocuments,
            List<VisitTask> alreadyHeldDocuments,
            List<AgencyVisit> visits,
            List<String> unknownCodes,
            int totalFee) {

        public VisitPlan {
            onlineDocuments = List.copyOf(onlineDocuments);
            alreadyHeldDocuments = List.copyOf(alreadyHeldDocuments);
            visits = List.copyOf(visits);
            unknownCodes = List.copyOf(unknownCodes);
        }
    }
}
