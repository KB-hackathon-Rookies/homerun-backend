package com.homerun.domain.contract.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 3루 완료 시 사용한 규칙 버전. */
public record ThirdBaseCompleteRequest(@NotBlank String ruleVersion) {}
