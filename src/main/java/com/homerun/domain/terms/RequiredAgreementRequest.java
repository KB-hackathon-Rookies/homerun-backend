package com.homerun.domain.terms;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record RequiredAgreementRequest(@NotEmpty List<@Valid AgreementItem> agreements) {}
