package com.homerun.domain.plan.type;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum PlanGate {
    BENCH_ONBOARDING("BENCH_ONBOARDING", "온보딩 완료", 1, PlanStage.BENCH, List.of(), false),
    FIRST_DIAGNOSIS("FIRST_DIAGNOSIS", "대출 진단·매물 안전 확인 완료", 2, PlanStage.FIRST, List.of("BENCH_ONBOARDING"), false),
    SECOND_POLICY_SELECTION(
            "SECOND_POLICY_SELECTION", "대출 상품·보증 확정 완료", 3, PlanStage.SECOND, List.of("FIRST_DIAGNOSIS"), false),
    THIRD_EXECUTION("THIRD_EXECUTION", "계약·대출 실행 완료", 4, PlanStage.THIRD, List.of("SECOND_POLICY_SELECTION"), true),
    HOME_SETTLEMENT("HOME_SETTLEMENT", "입주 후 정착 완료", 5, PlanStage.HOME, List.of("THIRD_EXECUTION"), false);

    private final String code;
    private final String displayName;
    private final int sequence;
    private final PlanStage stage;
    private final List<String> dependencies;
    private final boolean irreversible;

    PlanGate(
            String code,
            String displayName,
            int sequence,
            PlanStage stage,
            List<String> dependencies,
            boolean irreversible) {
        this.code = code;
        this.displayName = displayName;
        this.sequence = sequence;
        this.stage = stage;
        this.dependencies = dependencies;
        this.irreversible = irreversible;
    }

    public static Optional<PlanGate> findByCode(String code) {
        return Arrays.stream(values()).filter(gate -> gate.code.equals(code)).findFirst();
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public int sequence() {
        return sequence;
    }

    public PlanStage stage() {
        return stage;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public boolean irreversible() {
        return irreversible;
    }
}
