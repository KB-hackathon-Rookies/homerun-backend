package com.homerun.domain.diagnosis.dto.response;

public record DiagnosisCostResponse(
        long deposit,
        long movingCost,
        long brokerageFee,
        long guaranteeFee,
        long stampTax,
        long emergencyReserve,
        long totalRequired) {}
