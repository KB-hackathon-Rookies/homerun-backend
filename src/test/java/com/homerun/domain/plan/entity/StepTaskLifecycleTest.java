package com.homerun.domain.plan.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.homerun.domain.plan.type.StepTaskStatus;
import com.homerun.domain.plan.type.StepTaskTemplate;
import com.homerun.domain.plan.type.TaskRecurrence;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StepTaskLifecycleTest {

    @Test
    void should_expireIncompleteTask_when_dueDateHasPassed() {
        StepTask task = StepTask.of(1L, StepTaskTemplate.FIRST_MONTH_CHECKIN);
        task.schedule(LocalDate.of(2026, 9, 1), TaskRecurrence.MONTHLY);

        assertThat(task.expireIfOverdue(LocalDate.of(2026, 9, 2))).isTrue();
        assertThat(task.getStatus()).isEqualTo(StepTaskStatus.EXPIRED);
        assertThat(task.getRecurrence()).isEqualTo(TaskRecurrence.MONTHLY);
    }

    @Test
    void should_preserveCompletedTask_when_dueDateHasPassed() {
        StepTask task = StepTask.of(1L, StepTaskTemplate.GUARANTEE_CHECK);
        task.schedule(LocalDate.of(2026, 9, 1), TaskRecurrence.ONCE);
        task.complete();

        assertThat(task.expireIfOverdue(LocalDate.of(2026, 9, 2))).isFalse();
        assertThat(task.getStatus()).isEqualTo(StepTaskStatus.DONE);
    }
}
