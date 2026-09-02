package com.homerun.rent;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

/**
 * 주거급여 판정 입력.
 *
 * @param householdSize 가구원 수
 * @param recognizedIncome 소득인정액(원)
 * @param monthlyRent 실제 월 임차료(원)
 * @param age 나이(만)
 * @param married 혼인 여부
 * @param livesApartFromParents 부모와 주민등록상 시·군이 다른가
 * @param parentOnHousingBenefit 부모 가구가 이미 주거급여를 받고 있는가
 */
@Schema(description = "주거급여 판정 입력")
public record HousingBenefitRequest(
        @Min(value = 1, message = "가구원 수는 1 이상이어야 한다") int householdSize,
        @Min(value = 0, message = "소득인정액은 0원 이상이어야 한다") long recognizedIncome,
        @Min(value = 0, message = "월 임차료는 0원 이상이어야 한다") long monthlyRent,
        @Min(value = 0, message = "나이는 0 이상이어야 한다") int age,
        boolean married,
        boolean livesApartFromParents,
        boolean parentOnHousingBenefit) {}
