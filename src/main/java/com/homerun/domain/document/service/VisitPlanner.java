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
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발급처별 방문 계획(PLC-01-04 · PLC-01-05 · PLC-01-06).
 *
 * <p>서류 단위로만 안내하면 사용자가 같은 곳을 여러 번 간다. 전입세대확인서·주민등록등본·
 * 가족관계증명서는 주민센터 한 번이면 다 끝나는데, 서류마다 제일 좋은 방법을 따로 고르면
 * 등본은 무인발급기, 전입세대확인서는 창구로 갈려 두 걸음이 된다.
 *
 * <p>그래서 <b>서류마다 고르지 않고 걸음의 조합을 고른다.</b> 갈 곳 수를 먼저 줄이고, 같은
 * 걸음 수라면 그 안에서 싼 방법을 고른다.
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
     * @param onlineFirst false 면 온라인을 빼고 잡는다. 공동인증서나 프린터가 없는 경우,
     *     제출처가 관인 찍힌 원본을 요구하는 경우가 그렇다
     */
    @Transactional(readOnly = true)
    public VisitPlan plan(List<String> documentCodes, boolean onlineFirst) {
        List<String> codes = dedupe(documentCodes);
        Map<String, DocumentType> found = documents.findByCodeIn(codes).stream()
                .collect(Collectors.toMap(DocumentType::getCode, document -> document));
        Map<Long, List<DocumentIssueMethod>> methodsByDocument = loadMethods(found.values());

        List<String> unknown = new ArrayList<>();
        List<VisitTask> online = new ArrayList<>();
        List<VisitTask> alreadyHeld = new ArrayList<>();
        List<Candidate> needVisit = new ArrayList<>();

        for (String code : codes) {
            DocumentType document = found.get(code);
            if (document == null) {
                // 모르는 코드를 조용히 버리면 사용자는 그 서류가 필요 없다고 읽는다.
                unknown.add(code);
                continue;
            }
            List<DocumentIssueMethod> all = methodsByDocument.getOrDefault(document.getId(), List.of());
            if (all.isEmpty()) {
                unknown.add(code);
                continue;
            }
            List<DocumentIssueMethod> usable = usable(all, onlineFirst);
            if (usable.size() == 1) {
                DocumentIssueMethod only = usable.get(0);
                if (!only.getAgencyCode().visitRequired()) {
                    // 온라인이거나 이미 손에 있는 것. 걸음으로 세지 않는다.
                    (only.getAgencyCode() == IssueAgency.SELF ? alreadyHeld : online).add(toTask(document, only));
                    continue;
                }
            }
            needVisit.add(new Candidate(document, usable));
        }

        List<AgencyVisit> visits = toVisits(needVisit);
        return new VisitPlan(
                online,
                alreadyHeld,
                visits,
                unknown,
                visits.stream().mapToInt(AgencyVisit::totalFee).sum());
    }

    /** 서류 하나당 조회 한 번씩 돌면 목록이 길어질수록 쿼리가 배로 는다. 한 번에 읽는다. */
    private Map<Long, List<DocumentIssueMethod>> loadMethods(java.util.Collection<DocumentType> found) {
        if (found.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = found.stream().map(DocumentType::getId).toList();
        return methods.findByDocumentTypeIdInOrderBySortOrderAsc(ids).stream()
                .collect(Collectors.groupingBy(DocumentIssueMethod::getDocumentTypeId));
    }

    /**
     * 쓸 수 있는 발급방법.
     *
     * <p>{@code onlineFirst} 면 온라인이 있을 때 그것만 남긴다. 아니면 온라인을 뺀다. 다만
     * 온라인밖에 없는 서류는 그때도 온라인을 남긴다 — 방법이 그것뿐인데 빼 버리면 준비물
     * 목록에서 아예 사라진다.
     */
    private List<DocumentIssueMethod> usable(List<DocumentIssueMethod> all, boolean onlineFirst) {
        if (onlineFirst) {
            List<DocumentIssueMethod> online =
                    all.stream().filter(DocumentIssueMethod::online).toList();
            return online.isEmpty() ? all : online;
        }
        List<DocumentIssueMethod> offline =
                all.stream().filter(method -> !method.online()).toList();
        return offline.isEmpty() ? all : offline;
    }

    /**
     * 걸음 수를 먼저 줄인다(PLC-01-05).
     *
     * <p>갈 수 있는 곳의 조합을 작은 것부터 훑어, 모든 서류를 덮는 첫 조합을 쓴다. 발급처
     * 종류가 여덟 가지뿐이라 전부 세어도 256가지다. 같은 크기의 조합이 여럿이면 수수료가
     * 싼 쪽을 고른다.
     *
     * <p>서류마다 최선을 먼저 고르는 방식으로는 이게 안 된다. 등본에게 무인발급기가 제일
     * 싸다는 것과, 전체 걸음이 줄어든다는 것은 다른 이야기다.
     */
    private List<AgencyVisit> toVisits(List<Candidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<IssueAgency> reachable = candidates.stream()
                .flatMap(candidate -> candidate.agencies().stream())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(IssueAgency.class)));

        Set<IssueAgency> chosen = cheapestSmallestCover(candidates, List.copyOf(reachable));

        // 넣은 순서를 지킨다. 같은 곳으로 묶이는 서류가 흩어져 있어도 방문 순서는 안 바뀐다.
        Map<IssueAgency, List<Planned>> byAgency = new LinkedHashMap<>();
        for (Candidate candidate : candidates) {
            DocumentIssueMethod picked = candidate.cheapestIn(chosen).orElseThrow();
            byAgency.computeIfAbsent(picked.getAgencyCode(), key -> new ArrayList<>())
                    .add(new Planned(candidate.document(), picked));
        }
        return byAgency.entrySet().stream()
                .map(entry -> toVisit(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Set<IssueAgency> cheapestSmallestCover(List<Candidate> candidates, List<IssueAgency> reachable) {
        int total = 1 << reachable.size();
        Set<IssueAgency> best = null;
        int bestSize = Integer.MAX_VALUE;
        long bestFee = Long.MAX_VALUE;

        for (int mask = 1; mask < total; mask++) {
            int size = Integer.bitCount(mask);
            if (size > bestSize) {
                continue;
            }
            Set<IssueAgency> subset = EnumSet.noneOf(IssueAgency.class);
            for (int i = 0; i < reachable.size(); i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(reachable.get(i));
                }
            }
            long fee = 0;
            boolean covers = true;
            for (Candidate candidate : candidates) {
                Optional<DocumentIssueMethod> picked = candidate.cheapestIn(subset);
                if (picked.isEmpty()) {
                    covers = false;
                    break;
                }
                fee += picked.get().getFee();
            }
            if (covers && (size < bestSize || fee < bestFee)) {
                best = subset;
                bestSize = size;
                bestFee = fee;
            }
        }
        return best;
    }

    private List<String> dedupe(List<String> codes) {
        return new ArrayList<>(new LinkedHashSet<>(codes));
    }

    private AgencyVisit toVisit(IssueAgency agency, List<Planned> planned) {
        List<VisitTask> tasks = planned.stream()
                .map(item -> toTask(item.document(), item.method()))
                .toList();

        // 서류마다 "신분증"을 따로 적으면 체크리스트가 신분증으로 가득 찬다. 쉼표로 나눠 합친다.
        Set<String> checklist = new LinkedHashSet<>();
        Set<String> notes = new LinkedHashSet<>();
        for (Planned item : planned) {
            checklist.addAll(split(item.method().getRequirements()));
            if (item.method().getNote() != null) {
                notes.add(item.method().getNote());
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

    /** 서류 하나와 그 서류를 뗄 수 있는 방법들. */
    private record Candidate(DocumentType document, List<DocumentIssueMethod> methods) {

        Set<IssueAgency> agencies() {
            return methods.stream()
                    .map(DocumentIssueMethod::getAgencyCode)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(IssueAgency.class)));
        }

        /** 고른 걸음 안에서 가장 싼 방법. 같은 값이면 안내 순서가 앞선 것. */
        Optional<DocumentIssueMethod> cheapestIn(Set<IssueAgency> agencies) {
            return methods.stream()
                    .filter(method -> agencies.contains(method.getAgencyCode()))
                    .min(Comparator.comparingInt(DocumentIssueMethod::getFee)
                            .thenComparingInt(DocumentIssueMethod::getSortOrder));
        }
    }

    private record Planned(DocumentType document, DocumentIssueMethod method) {}
}
