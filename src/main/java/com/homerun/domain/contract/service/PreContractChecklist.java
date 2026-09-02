package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.ChecklistItem;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.type.ChecklistStatus;
import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.type.CheckResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 계약 전 체크리스트(PRP-02-02).
 *
 * <p>계약금을 보내기 전에 끝나 있어야 하는 것만 모은다. 앞의 셋은 PRP-01 매물 검증이 남긴
 * 판정을 서류 단위로 다시 묶은 것이고, 마지막 하나는 은행 상담이다.
 *
 * <p>검증을 아직 안 돌렸으면 TODO 다. 검증을 안 돌린 것과 돌려서 문제가 없는 것은 다르다.
 */
@Component
class PreContractChecklist {

    /** 서류 하나에 검증 항목 여러 개가 걸린다. 등기부 한 장으로 소유자·신탁·선순위를 본다. */
    private static final List<DocumentGroup> GROUPS = List.of(
            new DocumentGroup(
                    "REGISTRY",
                    "등기사항전부증명서",
                    Set.of("OWNER_MATCH", "TRUST_REGISTRATION", "JEONSE_RATIO"),
                    "인터넷등기소에서 등기사항전부증명서를 떼어 소유자와 선순위채권을 확인한다."),
            new DocumentGroup(
                    "BUILDING_LEDGER",
                    "건축물대장",
                    Set.of("VIOLATION_BUILDING", "MULTI_HOUSEHOLD"),
                    "정부24에서 건축물대장을 떼어 위반건축물 여부를 확인한다."),
            new DocumentGroup("OFFICIAL_PRICE", "공시가격", Set.of("OFFICIAL_PRICE_126"), "부동산공시가격 알리미에서 공시가격을 확인한다."));

    private final PropertyCheckRepository checks;

    PreContractChecklist(PropertyCheckRepository checks) {
        this.checks = checks;
    }

    List<ChecklistItem> build(LeaseContract contract) {
        Map<String, CheckResult> results = results(contract);

        List<ChecklistItem> items = new ArrayList<>();
        for (DocumentGroup group : GROUPS) {
            items.add(group.toItem(results));
        }
        items.add(bankConsult(contract));
        return items;
    }

    private Map<String, CheckResult> results(LeaseContract contract) {
        if (contract == null || contract.getPropertyId() == null) {
            return Map.of();
        }
        return checks.findByPropertyIdOrderById(contract.getPropertyId()).stream()
                .collect(Collectors.toMap(
                        PropertyCheck::getCheckCode, PropertyCheck::getResult, (first, second) -> second));
    }

    private ChecklistItem bankConsult(LeaseContract contract) {
        boolean done = contract != null && contract.getBankConsultedAt() != null;
        return new ChecklistItem(
                "BANK_CONSULT",
                "은행 사전상담",
                done ? ChecklistStatus.DONE : ChecklistStatus.TODO,
                done ? "%s 에 상담을 마쳤다.".formatted(contract.getBankConsultedAt()) : "이 매물로 대출이 되는지 확인하지 않았다.",
                done ? null : "계약금을 보내기 전에 은행에서 대출 가능 여부를 확인한다.");
    }

    /**
     * 서류 한 장과 그 서류로 확인하는 검증 항목들.
     *
     * @param requiredCodes 이 서류로 확인하는 항목 코드. 매물에 해당 없는 항목은 검증 결과에
     *     아예 없으므로 확인 못 한 것으로 치지 않는다
     */
    private record DocumentGroup(String code, String label, Set<String> requiredCodes, String action) {

        ChecklistItem toItem(Map<String, CheckResult> results) {
            List<CheckResult> mine = requiredCodes.stream()
                    .map(results::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            if (mine.isEmpty()) {
                return new ChecklistItem(
                        code, label, ChecklistStatus.TODO, "%s 를 아직 확인하지 않았다.".formatted(label), action);
            }
            if (mine.contains(CheckResult.BLOCK)) {
                return new ChecklistItem(
                        code,
                        label,
                        ChecklistStatus.BLOCKED,
                        "%s 확인에서 계약을 막아야 하는 문제가 나왔다.".formatted(label),
                        "매물 검증 결과에서 막힌 항목을 먼저 해결한다.");
            }
            if (mine.contains(CheckResult.UNKNOWN)) {
                // 모른다고 통과시키지 않는다(NFR-01-06). 확인이 남았다고만 말한다.
                return new ChecklistItem(
                        code, label, ChecklistStatus.TODO, "%s 에서 확인하지 못한 항목이 남아 있다.".formatted(label), action);
            }
            return new ChecklistItem(
                    code,
                    label,
                    ChecklistStatus.DONE,
                    mine.contains(CheckResult.WARN)
                            ? "%s 확인을 마쳤다. 알아 둘 점이 있다.".formatted(label)
                            : "%s 확인을 마쳤고 문제가 없다.".formatted(label),
                    null);
        }
    }
}
