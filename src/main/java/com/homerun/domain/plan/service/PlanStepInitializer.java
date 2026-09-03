package com.homerun.domain.plan.service;

import com.homerun.domain.plan.type.LeaseType;
import com.homerun.domain.plan.entity.PlanStep;
import com.homerun.domain.plan.entity.StepTask;
import com.homerun.domain.plan.repository.PlanStepRepository;
import com.homerun.domain.plan.repository.StepTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PlanStepInitializer {

    private final PlanStepRepository planStepRepository;
    private final StepTaskRepository stepTaskRepository;

    /**
     * Plan 생성 시 기본 Step과 Task를 생성한다.
     *
     * @param planId    생성된 Plan ID
     * @param leaseType 임대 유형 (전세 / 월세)
     */
    public void initialize(Long planId, LeaseType leaseType) {

        // 1. 대단계 생성
        List<PlanStep> steps = createSteps(planId);

        planStepRepository.saveAll(steps);

        // 2. 각 대단계에 해당하는 세부 Task 생성
        for (PlanStep step : steps) {
            List<StepTask> tasks = createTasks(step, leaseType);

            stepTaskRepository.saveAll(tasks);
        }
    }

    /**
     * Plan에 필요한 기본 대단계 1~4를 생성한다.
     *
     * 1루. 사용자 정보 입력 받기
     * 2루. 진단
     * 3루. 진행
     * 4루. 사후
     */
    private List<PlanStep> createSteps(Long planId) {

        return List.of(
                PlanStep.create(
                        planId,
                        "USER_INFO",
                        "사용자 정보 입력 받기",
                        1
                ),

                PlanStep.create(
                        planId,
                        "DIAGNOSIS",
                        "진단",
                        2
                ),

                PlanStep.create(
                        planId,
                        "PROGRESS",
                        "진행",
                        3
                ),

                PlanStep.create(
                        planId,
                        "AFTERCARE",
                        "사후",
                        4
                )
        );
    }

    /**
     * Step의 단계 번호에 따라 해당하는 Task를 생성한다.
     *
     * 전세와 월세는 같은 1~4단계 구조를 사용하지만,
     * 각 단계의 세부 Task는 LeaseType에 따라 달라질 수 있다.
     */
    private List<StepTask> createTasks(
            PlanStep step,
            LeaseType leaseType
    ) {

        return switch (step.getStepGroup()) {

            case 1 -> createPrepareTasks(
                    step,
                    leaseType
            );

            case 2 -> createSearchTasks(
                    step,
                    leaseType
            );

            case 3 -> createContractTasks(
                    step,
                    leaseType
            );

            case 4 -> createMoveInTasks(
                    step,
                    leaseType
            );

            default -> throw new IllegalArgumentException(
                    "잘못된 step group입니다. stepGroup=" + step.getStepGroup()
            );
        };
    }

    /**
     * 1단계 - 준비
     */
    private List<StepTask> createPrepareTasks(
            PlanStep step,
            LeaseType leaseType
    ) {

        if (leaseType == LeaseType.JEONSE) {

            return List.of(
                    StepTask.create(
                            step.getId(),
                            "BANK_CONSULTATION",
                            "은행 상담",
                            1
                    ),

                    StepTask.create(
                            step.getId(),
                            "LOAN_LIMIT_CHECK",
                            "전세대출 한도 확인",
                            2
                    ),

                    StepTask.create(
                            step.getId(),
                            "DOCUMENT_CHECK",
                            "대출 필요 서류 확인",
                            3
                    )
            );
        }

        // 월세
        return List.of(
                StepTask.create(
                        step.getId(),
                        "MONTHLY_SUPPORT_CHECK",
                        "월세 지원 정책 확인",
                        1
                ),

                StepTask.create(
                        step.getId(),
                        "MONTHLY_BUDGET_CHECK",
                        "월세 예산 확인",
                        2
                ),

                StepTask.create(
                        step.getId(),
                        "DOCUMENT_CHECK",
                        "필요 서류 확인",
                        3
                )
        );
    }

    /**
     * 2단계 - 집 찾기
     *
     * 현재는 전세/월세 공통 Task를 사용한다.
     */
    private List<StepTask> createSearchTasks(
            PlanStep step,
            LeaseType leaseType
    ) {

        return List.of(
                StepTask.create(
                        step.getId(),
                        "PROPERTY_SEARCH",
                        "매물 확인",
                        1
                ),

                StepTask.create(
                        step.getId(),
                        "BUILDING_REGISTER_CHECK",
                        "건축물대장 확인",
                        2
                ),

                StepTask.create(
                        step.getId(),
                        "ACTUAL_PRICE_CHECK",
                        "실거래가 확인",
                        3
                )
        );
    }

    /**
     * 3단계 - 계약
     */
    private List<StepTask> createContractTasks(
            PlanStep step,
            LeaseType leaseType
    ) {

        if (leaseType == LeaseType.JEONSE) {

            return List.of(
                    StepTask.create(
                            step.getId(),
                            "REGISTER_CHECK",
                            "등기부등본 확인",
                            1
                    ),

                    StepTask.create(
                            step.getId(),
                            "CONTRACT_CHECK",
                            "계약서 확인",
                            2
                    ),

                    StepTask.create(
                            step.getId(),
                            "SPECIAL_CLAUSE_CHECK",
                            "특약 확인",
                            3
                    ),

                    StepTask.create(
                            step.getId(),
                            "DEPOSIT_PAYMENT",
                            "계약금 지급",
                            4
                    )
            );
        }

        // 월세
        return List.of(
                StepTask.create(
                        step.getId(),
                        "CONTRACT_CHECK",
                        "계약서 확인",
                        1
                ),

                StepTask.create(
                        step.getId(),
                        "SPECIAL_CLAUSE_CHECK",
                        "특약 확인",
                        2
                ),

                StepTask.create(
                        step.getId(),
                        "DEPOSIT_PAYMENT",
                        "보증금 및 계약금 지급",
                        3
                )
        );
    }

    /**
     * 4단계 - 입주
     */
    private List<StepTask> createMoveInTasks(
            PlanStep step,
            LeaseType leaseType
    ) {

        if (leaseType == LeaseType.JEONSE) {

            return List.of(
                    StepTask.create(
                            step.getId(),
                            "BALANCE_PAYMENT",
                            "잔금 지급",
                            1
                    ),

                    StepTask.create(
                            step.getId(),
                            "MOVE_IN_REPORT",
                            "전입신고",
                            2
                    ),

                    StepTask.create(
                            step.getId(),
                            "FIXED_DATE",
                            "확정일자 확인",
                            3
                    ),

                    StepTask.create(
                            step.getId(),
                            "GUARANTEE_CHECK",
                            "전세보증 가입 확인",
                            4
                    )
            );
        }

        // 월세
        return List.of(
                StepTask.create(
                        step.getId(),
                        "BALANCE_PAYMENT",
                        "잔금 지급",
                        1
                ),

                StepTask.create(
                        step.getId(),
                        "MOVE_IN_REPORT",
                        "전입신고",
                        2
                ),

                StepTask.create(
                        step.getId(),
                        "FIXED_DATE",
                        "확정일자 확인",
                        3
                )
        );
    }
}


