package com.homerun.domain.property.type;

public enum PropertyDiagnosisStep {
    BUILDING(2),
    VIOLATION(3),
    REGISTRY(4),
    COMPLETE(5);

    private final int number;

    PropertyDiagnosisStep(int number) {
        this.number = number;
    }

    public int number() {
        return number;
    }
}
