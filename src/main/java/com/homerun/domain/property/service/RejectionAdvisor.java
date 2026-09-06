package com.homerun.domain.property.service;

import com.homerun.domain.property.dto.response.RejectionGuidanceResponse;
import com.homerun.domain.property.type.CollateralMethod;
import com.homerun.domain.property.type.RejectionCategory;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 거절 사유 분류별 대안을 낸다(BR-24, FR-P8-01). 판정이 아니라 정해진 대응 안내라 사유 분류와
 * (보증기관 거절일 때) 거절된 담보 방식만 받아 문구를 고른다.
 *
 * <p>핵심은 "다른 은행에 가도 소용없는 경우"를 구분하는 것이다 — 사람·집 자체 문제면 은행을
 * 바꿔도 같은 결과다. 보증기관 거절은 같은 은행에서 담보를 바꾸는 게 대안이다.
 */
@Component
class RejectionAdvisor {

    public RejectionGuidanceResponse guide(RejectionCategory category, CollateralMethod rejectedCollateral) {
        return switch (category) {
            case SUBJECT_ISSUE ->
                new RejectionGuidanceResponse(
                        category,
                        "사람(소득·신용·무주택) 조건 문제예요. 다른 은행에 가도 결과가 같아요.",
                        List.of(
                                "소득·재직 조건을 다시 확인하고, 맞는 상품(청년/일반 버팀목 등)으로 바꿔 보세요.",
                                "보증금을 낮춘 매물로 필요한 대출액을 줄이면 통과 가능성이 올라가요.",
                                "무주택 요건(분양권·입주권 포함)을 다시 확인하세요."),
                        true);
            case PROPERTY_ISSUE ->
                new RejectionGuidanceResponse(
                        category,
                        "집 문제예요. 위반건축물·근생이면 매물을 바꿔야 하고, 근저당 과다·시세 불명이면 보증기관 변경 여지가 있어요.",
                        List.of(
                                "위반건축물·근린생활시설(근생빌라)이 원인이면 이 매물로는 어떤 은행·보증기관도 안 돼요 — 매물을 바꾸세요.",
                                "근저당 과다·시세 불명이 원인이면 다른 보증기관(HUG/HF/SGI)에서 될 여지가 있어요.",
                                "집 자체 문제는 다른 은행에 가도 대부분 같은 결과예요."),
                        true);
            case GUARANTEE_ISSUE ->
                new RejectionGuidanceResponse(
                        category,
                        "보증기관 거절이에요. 은행을 바꾸지 말고 같은 은행에서 담보 방식을 바꿔 보세요.",
                        guaranteeAlternatives(rejectedCollateral),
                        false);
            case DOCUMENT_ISSUE ->
                new RejectionGuidanceResponse(
                        category,
                        "서류 미비예요. 보완하면 다시 신청할 수 있어요.",
                        List.of("부족한 서류를 확인해 보완한 뒤 같은 은행에 재신청하세요.", "재신청도 심사 기간이 다시 걸리니 잔금일까지 여유를 확인하세요."),
                        false);
            case LANDLORD_ISSUE ->
                new RejectionGuidanceResponse(
                        category,
                        "임대인 문제예요. 특약을 발동하거나 임대인을 설득해야 해요.",
                        List.of(
                                "대출 미승인 시 계약 무효 특약이 있으면 발동해 계약금을 돌려받을 수 있어요.",
                                "임대인이 전세대출 협조를 거부한 경우라면 설득하거나 다른 매물을 알아보세요."),
                        false);
        };
    }

    /** 보증기관 거절 시 담보 변경 경로(BR-24): HUG→SGI·HF, SGI→정책형, HF→SGI. */
    private List<String> guaranteeAlternatives(CollateralMethod rejected) {
        List<String> paths = new ArrayList<>();
        if (rejected == null) {
            paths.add("어느 보증기관에서 거절됐는지 확인하면 바꿀 담보 방식을 알려드릴 수 있어요.");
            return paths;
        }
        switch (rejected) {
            case HUG_SAFE_JEONSE -> {
                paths.add("HUG 거절이면 같은 은행에서 SGI 또는 HF 담보로 다시 상담해 보세요.");
            }
            case SGI -> {
                paths.add("SGI 거절이면 정책형(버팀목 등 기금) 대출을 다시 검토해 보세요.");
            }
            case HF -> {
                paths.add("HF 거절이면 같은 은행에서 SGI 담보로 다시 상담해 보세요.");
            }
            default -> paths.add("거절된 담보 방식을 확인하면 바꿀 방식을 알려드릴 수 있어요.");
        }
        return paths;
    }
}
