package com.homerun.domain.plan.entity;

import com.homerun.domain.plan.type.PlanStepStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 사용자의 독립 계획(Plan)에 포함된 대단계 진행 정보를 관리하는 엔티티.
 *
 * 하나의 Plan은 여러 개의 PlanStep을 가지며,
 * 현재 프로젝트에서는 1~4단계의 대단계로 구성된다.
 *
 * 예)
 * 1. 준비
 * 2. 집 찾기
 * 3. 계약
 * 4. 입주
 *
 * 실제 세부 체크 항목은 StepTask에서 관리한다.
 */
@Entity
@Table(
        name = "plan_step",
        uniqueConstraints = {
                // 하나의 Plan 안에서는 동일한 step_group을 중복해서 가질 수 없음.
                // 예: plan_id = 1에 step_group = 1인 Step은 하나만 존재.
                @UniqueConstraint(
                        name = "uq_plan_step_plan_group",
                        columnNames = {"plan_id", "step_group"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanStep {

    /**
     * PlanStep의 PK.
     * DB에서 자동으로 증가한다.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 해당 Step이 속한 Plan의 ID.
     *
     * 하나의 Plan은 여러 개의 PlanStep을 가진다.
     */
    @Column(name = "plan_id", nullable = false)
    private Long planId;

    /**
     * Step을 식별하기 위한 고유 코드.
     *
     * 예)
     * PREPARE
     * SEARCH
     * CONTRACT
     * MOVE_IN
     */
    @Column(name = "step_code", nullable = false, length = 50)
    private String stepCode;

    /**
     * 사용자에게 화면에 표시할 Step 이름.
     *
     * 예)
     * 준비
     * 집 찾기
     * 계약
     * 입주
     */
    @Column(name = "step_name", nullable = false, length = 200)
    private String stepName;

    /**
     * 대단계의 순서.
     *
     * 현재 프로젝트에서는 1~4단계로 구성된다.
     *
     * 1 = 준비
     * 2 = 집 찾기
     * 3 = 계약
     * 4 = 입주
     */
    @Column(name = "step_group", nullable = false)
    private Integer stepGroup;

    /**
     * 현재 Step의 진행 상태.
     *
     * LOCKED : 아직 진행할 수 없음
     * READY  : 진행할 수 있음
     * DOING  : 현재 진행 중
     * DONE   : 완료
     * SKIPPED: 건너뜀
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlanStepStatus status;

    /**
     * 해당 Step이 되돌릴 수 없는 단계인지 여부.
     *
     * 예)
     * 계약금 지급과 같이 실제 금전 거래가 발생하는 단계라면
     * 되돌릴 수 없는 단계로 설정할 수 있다.
     */
    @Column(name = "is_irreversible", nullable = false)
    private boolean irreversible;

    /**
     * Step이 완료된 시간.
     *
     * 아직 완료되지 않았다면 null이다.
     */
    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * Step 데이터가 생성된 시간.
     */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * Step 데이터가 마지막으로 수정된 시간.
     */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * 새로운 PlanStep을 생성한다.
     * Plan 생성 시 기본 Step 1~4를 생성할 때 사용한다.
     *
     * @param planId    Step이 속한 Plan ID
     * @param stepCode  Step 식별 코드
     * @param stepName  Step 이름
     * @param stepGroup 대단계 순서 (1~4)
     * @return 생성된 PlanStep
     */
    public static PlanStep create(
            Long planId,
            String stepCode,
            String stepName,
            int stepGroup
    ) {
        PlanStep step = new PlanStep();

        // 어떤 Plan에 속한 Step인지 설정
        step.planId = planId;

        // Step 식별 코드 설정
        step.stepCode = stepCode;

        // 화면에 표시할 Step 이름 설정
        step.stepName = stepName;

        // 대단계 순서 설정
        step.stepGroup = stepGroup;

        /*
         * Plan 생성 직후에는 1단계만 바로 진행할 수 있도록 한다.
         *
         * 1단계 → READY
         * 2~4단계 → LOCKED
         *
         * 이후 사용자가 1단계를 완료하면
         * 다음 단계의 상태를 READY로 변경한다.
         */
        step.status = stepGroup == 1
                ? PlanStepStatus.READY
                : PlanStepStatus.LOCKED;

        // 기본적으로 되돌릴 수 없는 단계가 아님
        step.irreversible = false;

        // 생성 시간 기록
        step.createdAt = Instant.now();

        // 마지막 수정 시간 기록
        step.updatedAt = Instant.now();

        return step;

    }

    public static List<PlanStep> defaultSteps(Long planId) {
        return List.of(
                create(planId, "PREPARE", "준비", 1),
                create(planId, "SEARCH", "집 찾기", 2),
                create(planId, "CONTRACT", "계약", 3),
                create(planId, "MOVE_IN", "입주", 4)
        );
    }

    public void start() {
        this.status = PlanStepStatus.DOING;
        this.updatedAt = Instant.now();
    }

    public void complete() {
        this.status = PlanStepStatus.DONE;
        this.completedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void ready() {
        this.status = PlanStepStatus.READY;
        this.updatedAt = Instant.now();
    }

    public void reset() {
        this.status = stepGroup == 1
                ? PlanStepStatus.READY
                : PlanStepStatus.LOCKED;

        this.completedAt = null;
        this.updatedAt = Instant.now();
    }

    public int getSequence() {
        return 0;
    }
}

