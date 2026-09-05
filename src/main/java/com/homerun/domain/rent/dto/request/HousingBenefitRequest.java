package com.homerun.domain.rent.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 주거급여 판정 입력.
 *
 * <p><b>가구원 수와 소득인정액은 신청 가구(본인이 속한 가구) 기준이다.</b> 청년 주거급여
 * 분리지급의 소득 판정은 부모 가구 기준이라(FCT-044) 이 값으로 판정하지 않는다 — 요건을 채운
 * 경우 소득은 추가확인으로 넘어간다.
 *
 * @param householdSize 신청 가구의 가구원 수
 * @param recognizedIncome 신청 가구의 소득인정액(원). 부모 가구 값이 아니다
 * @param monthlyRent 실제 월 임차료(원)
 * @param age 나이(만)
 * @param married 혼인 여부
 * @param livesApartFromParents 부모와 주민등록상 시·군이 다른가
 * @param parentOnHousingBenefit 부모 가구가 이미 주거급여를 받고 있는가
 */
@Schema(description = "주거급여 판정 입력. 가구원 수와 소득인정액은 신청 가구 기준이다")
public record HousingBenefitRequest(
        @Min(value = 1, message = "가구원 수는 1 이상이어야 한다") @Schema(description = "신청 가구의 가구원 수")
        int householdSize,

        @Min(value = 0, message = "소득인정액은 0원 이상이어야 한다")
        @Schema(description = "신청 가구의 소득인정액(원). 분리지급 소득 판정은 부모 가구 기준이라 이 값을 쓰지 않는다")
        long recognizedIncome,

        @Min(value = 0, message = "월 임차료는 0원 이상이어야 한다") long monthlyRent,
        @Min(value = 0, message = "나이는 0 이상이어야 한다") int age,
        boolean married,

        @Schema(description = "부모와 주민등록상 시·군이 다른가(FCT-043)") boolean livesApartFromParents,

        @Schema(description = "부모 가구가 이미 주거급여를 받고 있는가(FCT-044). 청년 단독 신청은 불가하다")
        boolean parentOnHousingBenefit) {}
