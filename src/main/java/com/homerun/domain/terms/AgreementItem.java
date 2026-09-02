package com.homerun.domain.terms;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AgreementItem(
        @NotBlank String code,
        @NotBlank String version,
        @NotNull Boolean agreed) {}
