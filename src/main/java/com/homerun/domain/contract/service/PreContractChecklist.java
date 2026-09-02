package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.ChecklistItem;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.type.ChecklistStatus;
import com.homerun.domain.property.entity.PropertyCheck;
import com.homerun.domain.property.repository.PropertyCheckRepository;
import com.homerun.domain.property.type.CheckResult;
import java.util.ArrayList;
import java.util.LinkedHashSet;
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
 * <p>서류가 통과하려면 그 서류로 봐야 할 항목이 <b>전부</b> 있어야 한다. 일부만 있는 상태를
 * 통과로 보면 빠진 항목이 영영 드러나지 않는다. 검증을 아예 안 돌린 것도 마찬가지다.
 */
@Component
class PreContractChecklist {

    /** 서류 하나에 검증 항목 여러 개가 걸린다. 등기부 한 장으로 소유자·신탁·선순위를 본다. */
    private static final List<DocumentGroup> GROUPS = List.of(
            new DocumentGroup(
                    "REGISTRY",
                    "등기사항전부증명서",
                    Set.of("OWNER_MATCH", "TRUST_REGISTRATION"),
                    Set.of("JEONSE_RATIO"),
                    "인터넷등기소에서 등기사항전부증명서를 떼어 소유자와 선순위채권을 확인한다."),
            new DocumentGroup(
                    "BUILDING_LEDGER",
                    "건축물대장",
                    Set.of("VIOLATION_BUILDING", "MULTI_HOUSEHOLD"),
                    Set.of(),
                    "정부24에서 건축물대장을 떼어 위반건축물 여부를 확인한다."),
            new DocumentGroup(
                    "OFFICIAL_PRICE", "공시가격", Set.of(), Set.of("OFFICIAL_PRICE_126"), "부동산공시가격 알리미에서 공시가격을 확인한다."));

    private final PropertyCheckRepository checks;

    PreContractChecklist(PropertyCheckRepository checks) {
        this.checks = checks;
    }

    List<ChecklistItem> build(LeaseContract contract) {
        Map<String, CheckResult> results = results(contract);
        boolean depositAtStake = depositAtStake(contract);

        List<ChecklistItem> items = new ArrayList<>();
        for (DocumentGroup group : GROUPS) {
            Set<String> required = group.requiredCodes(depositAtStake);
            if (required.isEmpty()) {
                // 보증금이 없으면 반환보증을 따질 일이 없다. 할 필요가 없는 것을 할 일로
                // 세우면 체크리스트가 끝나지 않는다.
                continue;
            }
            items.add(group.toItem(required, results));
        }
        items.add(bankConsult(contract));
        return items;
    }

    /**
     * 보증금이 걸린 계약으로 볼 것인가.
     *
     * <p>계약 정보를 아직 안 넣었으면 걸린 것으로 본다. 모른다는 이유로 확인 항목을 빼면
     * 그것이 곧 모름을 통과로 취급하는 일이다(NFR-01-06).
     *
     * <p>보증금 유무로 갈리는 항목 목록은 {@code PropertyRiskRule} 구현들의 {@code appliesTo}
     * 와 같아야 한다. 규칙을 더할 때 여기도 같이 본다.
     */
    private boolean depositAtStake(LeaseContract contract) {
        return contract == null || contract.hasDeposit();
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
     * @param alwaysCodes 계약 조건과 무관하게 항상 확인하는 항목
     * @param depositCodes 보증금이 걸린 계약에서만 확인하는 항목
     */
    private record DocumentGroup(
            String code, String label, Set<String> alwaysCodes, Set<String> depositCodes, String action) {

        Set<String> requiredCodes(boolean depositAtStake) {
            if (!depositAtStake) {
                return alwaysCodes;
            }
            Set<String> all = new LinkedHashSet<>(alwaysCodes);
            all.addAll(depositCodes);
            return all;
        }

        ChecklistItem toItem(Set<String> required, Map<String, CheckResult> results) {
            if (required.stream().map(results::get).anyMatch(result -> result == CheckResult.BLOCK)) {
                return new ChecklistItem(
                        code,
                        label,
                        ChecklistStatus.BLOCKED,
                        "%s 확인에서 계약을 막아야 하는 문제가 나왔다.".formatted(label),
                        "매물 검증 결과에서 막힌 항목을 먼저 해결한다.");
            }

            List<String> unresolved = required.stream()
                    .filter(checkCode -> {
                        CheckResult result = results.get(checkCode);
                        // 결과가 없는 것과 모른다는 결과는 똑같이 확인이 안 끝난 것이다.
                        return result == null || result == CheckResult.UNKNOWN;
                    })
                    .toList();

            if (unresolved.size() == required.size()) {
                return new ChecklistItem(
                        code, label, ChecklistStatus.TODO, "%s 를 아직 확인하지 않았다.".formatted(label), action);
            }
            if (!unresolved.isEmpty()) {
                return new ChecklistItem(
                        code,
                        label,
                        ChecklistStatus.TODO,
                        "%s 에서 확인하지 못한 항목이 %d건 남아 있다.".formatted(label, unresolved.size()),
                        action);
            }

            boolean warned = required.stream().map(results::get).anyMatch(result -> result == CheckResult.WARN);
            return new ChecklistItem(
                    code,
                    label,
                    ChecklistStatus.DONE,
                    warned ? "%s 확인을 마쳤다. 알아 둘 점이 있다.".formatted(label) : "%s 확인을 마쳤고 문제가 없다.".formatted(label),
                    null);
        }
    }
}
