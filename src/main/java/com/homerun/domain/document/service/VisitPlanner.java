package com.homerun.domain.document.service;

import com.homerun.domain.document.dto.VisitPlanDtos.AgencyVisit;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitPlan;
import com.homerun.domain.document.dto.VisitPlanDtos.VisitTask;
import com.homerun.domain.document.entity.DocumentIssueMethod;
import com.homerun.domain.document.entity.DocumentType;
import com.homerun.domain.document.repository.DocumentIssueMethodRepository;
import com.homerun.domain.document.repository.DocumentTypeRepository;
import com.homerun.domain.document.type.IssueAgency;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발급처별 방문 계획(PLC-01-04 · PLC-01-05 · PLC-01-06).
 *
 * <p>서류 단위로만 보여 주면 사용자가 같은 주민센터를 두 번 간다. 전입세대확인서와
 * 주민등록등본은 같은 창구에서 한 번에 끝나는데 목록은 그걸 말해 주지 않는다.
 *
 * <p>그래서 <b>걸음 수를 기준으로</b> 다시 묶는다. 집에서 되는 것은 방문에서 빼고, 가야 할
 * 곳은 한 번의 방문으로 합쳐 챙길 것과 할 일을 함께 준다.
 */
@Service
public class VisitPlanner {

    private static final String MAP_SEARCH = "https://map.kakao.com/?q=";

    private final DocumentTypeRepository documents;
    private final DocumentIssueMethodRepository methods;

    public VisitPlanner(DocumentTypeRepository documents, DocumentIssueMethodRepository methods) {
        this.documents = documents;
        this.methods = methods;
    }

    /** 온라인으로 되는 것은 온라인으로 본다. */
    @Transactional(readOnly = true)
    public VisitPlan plan(List<String> documentCodes) {
        return plan(documentCodes, true);
    }

    /**
     * @param onlineFirst false 면 전부 방문으로 잡는다. 공동인증서나 프린터가 없는 경우,
     *     제출처가 관인 찍힌 원본을 요구하는 경우가 그렇다. 이때 같은 곳끼리 묶는 것이
     *     실제로 걸음을 줄인다
     */
    @Transactional(readOnly = true)
    public VisitPlan plan(List<String> documentCodes, boolean onlineFirst) {
        List<VisitTask> online = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        // 요청 순서를 유지한다. 같은 곳으로 묶이는 서류가 흩어져 있어도 방문 순서는 바뀌지 않는다.
        Map<IssueAgency, List<Visit>> byAgency = new LinkedHashMap<>();

        for (String code : dedupe(documentCodes)) {
            Optional<DocumentType> found = documents.findByCode(code);
            if (found.isEmpty()) {
                // 모르는 코드를 조용히 버리면 사용자는 그 서류가 필요 없다고 읽는다.
                unknown.add(code);
                continue;
            }
            DocumentType document = found.get();
            Optional<DocumentIssueMethod> best = recommend(document, onlineFirst);
            if (best.isEmpty()) {
                unknown.add(code);
                continue;
            }
            DocumentIssueMethod method = best.get();
            if (method.getAgencyCode() == IssueAgency.ONLINE) {
                online.add(toTask(document, method));
                continue;
            }
            byAgency.computeIfAbsent(method.getAgencyCode(), key -> new ArrayList<>())
                    .add(new Visit(document, method));
        }

        List<AgencyVisit> visits = byAgency.entrySet().stream()
                .map(entry -> toVisit(entry.getKey(), entry.getValue()))
                .toList();

        return new VisitPlan(
                online,
                visits,
                unknown,
                visits.stream().mapToInt(AgencyVisit::totalFee).sum());
    }

    /** 같은 서류를 두 번 적어도 한 번만 뗀다. 순서는 처음 나온 자리를 지킨다. */
    private List<String> dedupe(List<String> codes) {
        return new ArrayList<>(new LinkedHashSet<>(codes));
    }

    /**
     * 어떤 방법으로 뗄지 고른다.
     *
     * <p>{@code onlineFirst} 면 ISS-01-02 와 같은 기준이다. 아니면 온라인을 빼고 가장 앞선
     * 방법을 고른다. 온라인밖에 없는 서류는 그때도 온라인으로 둔다 — 방법이 그것뿐인데
     * 빼 버리면 준비물 목록에서 아예 사라진다.
     */
    private Optional<DocumentIssueMethod> recommend(DocumentType document, boolean onlineFirst) {
        List<DocumentIssueMethod> all = methods.findByDocumentTypeIdOrderBySortOrderAsc(document.getId());
        if (!onlineFirst) {
            Optional<DocumentIssueMethod> offline = all.stream()
                    .filter(method -> !method.online())
                    .min(Comparator.comparingInt(DocumentIssueMethod::getSortOrder));
            if (offline.isPresent()) {
                return offline;
            }
        }
        return all.stream()
                .min(Comparator.comparing(DocumentIssueMethod::online)
                        .reversed()
                        .thenComparingInt(DocumentIssueMethod::getSortOrder));
    }

    private AgencyVisit toVisit(IssueAgency agency, List<Visit> planned) {
        List<VisitTask> tasks = planned.stream()
                .map(visit -> toTask(visit.document(), visit.method()))
                .toList();

        // 서류마다 "신분증"을 따로 적으면 체크리스트가 신분증으로 가득 찬다. 쉼표로 나눠 합친다.
        Set<String> checklist = new LinkedHashSet<>();
        Set<String> notes = new LinkedHashSet<>();
        for (Visit visit : planned) {
            checklist.addAll(split(visit.method().getRequirements()));
            if (visit.method().getNote() != null) {
                notes.add(visit.method().getNote());
            }
        }

        return new AgencyVisit(
                agency.label(),
                tasks,
                List.copyOf(checklist),
                tasks.stream().mapToInt(VisitTask::fee).sum(),
                directionsUrl(agency),
                List.copyOf(notes));
    }

    private List<String> split(String requirements) {
        if (requirements == null || requirements.isBlank()) {
            return List.of();
        }
        return Arrays.stream(requirements.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String directionsUrl(IssueAgency agency) {
        if (!agency.navigable()) {
            return null;
        }
        return MAP_SEARCH + URLEncoder.encode(agency.directionsQuery(), StandardCharsets.UTF_8);
    }

    private VisitTask toTask(DocumentType document, DocumentIssueMethod method) {
        return new VisitTask(
                document.getCode(), document.getName(), method.getFee(), method.getFeeNote(), method.getRequirements());
    }

    private record Visit(DocumentType document, DocumentIssueMethod method) {}
}
