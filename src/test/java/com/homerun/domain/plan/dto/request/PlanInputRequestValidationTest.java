package com.homerun.domain.plan.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlanInputRequestValidationTest {

    private final Validator validator =
            Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void should_rejectAmountsAndAreaOutsideAllowedRange() {
        PlanInputRequest request = new PlanInputRequest(
                -1L, 0L, -100L, null, null, null, BigDecimal.ZERO, null, null, null, null, null, null, null, Set.of());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("hopeDeposit", "monthlyRent", "areaM2");
    }
}
