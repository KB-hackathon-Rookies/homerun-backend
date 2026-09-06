package com.homerun.domain.property.dto.response;

import com.homerun.domain.property.entity.Property;
import com.homerun.domain.property.type.PropertyDiagnosisStep;
import com.homerun.domain.property.type.PropertyWorkflowStatus;

public record PropertyWorkflowResponse(
        Long propertyId,
        PropertyDiagnosisStep currentStep,
        int currentStepNumber,
        PropertyWorkflowStatus status,
        int revision) {

    public static PropertyWorkflowResponse from(Property property) {
        return new PropertyWorkflowResponse(
                property.getId(),
                property.getWorkflowStep(),
                property.getWorkflowStep().number(),
                property.getWorkflowStatus(),
                property.getWorkflowRevision());
    }
}
