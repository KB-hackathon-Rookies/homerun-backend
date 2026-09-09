package com.homerun.domain.property.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;
import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PropertyTest {

    /** VIOLATION 까지 걸어 BLOCKED(위반건축물=있어요) 상태로 만든다. revision 은 3 이 된다. */
    private Property blockedAtViolation() {
        Property property = Property.candidate(
                1L, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        property.completeBuildingStep(1, HouseType.APARTMENT, new BigDecimal("59.90")); // -> VIOLATION, rev2
        property.completeViolationStep(2, true); // 위반건축물 있어요 -> BLOCKED, step VIOLATION, rev3
        return property;
    }

    @Test
    @DisplayName("BLOCKED 여도 현재 STEP 은 답을 고쳐 다시 저장할 수 있다")
    void should_allowResavingCurrentStep_when_blocked() {
        Property property = blockedAtViolation();
        assertThat(property.getWorkflowStatus()).isEqualTo(PropertyWorkflowStatus.BLOCKED);

        // 실수를 정정 — 위반건축물 없어요 로 같은 STEP 을 다시 저장.
        assertThatCode(() -> property.completeViolationStep(3, false)).doesNotThrowAnyException();

        assertThat(property.getWorkflowStep()).isEqualTo(PropertyDiagnosisStep.REGISTRY);
        assertThat(property.getWorkflowStatus()).isEqualTo(PropertyWorkflowStatus.IN_PROGRESS);
        assertThat(property.getViolationBuilding()).isFalse();
    }

    @Test
    @DisplayName("BLOCKED 라도 다른 STEP 저장은 여전히 막힌다")
    void should_rejectSavingDifferentStep_when_blocked() {
        Property property = blockedAtViolation(); // step VIOLATION, rev3

        assertThatThrownBy(() -> property.completeRegistryStep(
                        3, null, null, null, null, null, null, null, null, null, null, null))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.errorCode()).isEqualTo(ErrorCode.PROPERTY_WORKFLOW_STEP_INVALID));
    }
}
