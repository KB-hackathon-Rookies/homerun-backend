//package com.homerun.domain.plan;
//
//import java.util.List;
//
//public record PlanProgressResponse(
//        Long planId,
//        PlanStage currentStage,
//        PlanStatus planStatus,
//        String lastLocationCode,
//        int completedSteps,
//        int totalSteps,
//        int progressPercent,
//        List<PlanStepResponse> steps) {
//
//    public static PlanProgressResponse from(Plan plan, List<PlanStep> steps) {
//        int completed = (int) steps.stream()
//                .filter(step -> step.getStatus() == PlanStepStatus.DONE || step.getStatus() == PlanStepStatus.SKIPPED)
//                .count();
//        int percent = steps.isEmpty() ? 0 : completed * 100 / steps.size();
//        return new PlanProgressResponse(
//                plan.getId(),
//                plan.getStage(),
//                plan.getStatus(),
//                plan.getLastLocationCode(),
//                completed,
//                steps.size(),
//                percent,
//                steps.stream().map(PlanStepResponse::from).toList());
//    }
//}
