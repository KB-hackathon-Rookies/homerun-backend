package com.homerun.domain.plan.service;

import com.homerun.domain.plan.dto.request.PlanInputRequest;
import com.homerun.domain.plan.dto.response.PlanInputResponse;
import com.homerun.domain.plan.entity.Plan;
import com.homerun.domain.plan.entity.PlanInput;
import com.homerun.domain.plan.repository.PlanInputRepository;
import com.homerun.domain.plan.repository.PlanRepository;
import com.homerun.domain.region.entity.Region;
import com.homerun.domain.region.repository.RegionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PlanInputService {

    private final PlanInputRepository planInputRepository;
    private final PlanRepository planRepository;
    private final RegionRepository regionRepository;

    public PlanInputResponse save(Long planId, PlanInputRequest request) {

        // 1. Plan 조회
        Plan plan = planRepository.findById(planId).orElseThrow(() -> new IllegalArgumentException("존재하지 않는 Plan입니다."));

        // 2. Region 조회
        Region region = null;

        if (request.getRegionId() != null) {
            region = regionRepository
                    .findById(request.getRegionId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 지역입니다."));
        }

        // 3. PlanInput 생성
        PlanInput planInput = PlanInput.builder()
                .plan(plan)
                .hopeDeposit(request.getHopeDeposit())
                .currentDeposit(request.getCurrentDeposit())
                .monthlyRent(request.getMonthlyRent())
                .maintenanceFee(request.getMaintenanceFee())
                .maxMonthlyBurden(request.getMaxMonthlyBurden())
                .region(region)
                .areaM2(request.getAreaM2())
                .houseType(request.getHouseType())
                .isHomeless(request.getIsHomeless())
                .householderStatus(request.getHouseholderStatus())
                .maritalStatus(request.getMaritalStatus())
                .employmentType(request.getEmploymentType())
                .employmentMonths(request.getEmploymentMonths())
                .companySize(request.getCompanySize())
                .unknownFields(request.getUnknownFields())
                .build();

        // 5. DB 저장
        PlanInput saved = planInputRepository.save(planInput);

        // 6. Response 반환
        return toResponse(saved);
    }

    private PlanInputResponse toResponse(PlanInput planInput) {

        return PlanInputResponse.builder()
                .id(planInput.getId())
                .planId(planInput.getPlan().getId())

                // 주거 비용
                .hopeDeposit(planInput.getHopeDeposit())
                .currentDeposit(planInput.getCurrentDeposit())
                .monthlyRent(planInput.getMonthlyRent())
                .maintenanceFee(planInput.getMaintenanceFee())
                .maxMonthlyBurden(planInput.getMaxMonthlyBurden())

                // 주거 조건
                .regionId(planInput.getRegion() != null ? planInput.getRegion().getId() : null)
                .areaM2(planInput.getAreaM2())
                .houseType(planInput.getHouseType())

                // 가구 조건
                .isHomeless(planInput.getIsHomeless())
                .householderStatus(planInput.getHouseholderStatus())
                .maritalStatus(planInput.getMaritalStatus())

                // 직업 조건
                .employmentType(planInput.getEmploymentType())
                .employmentMonths(planInput.getEmploymentMonths())
                .companySize(planInput.getCompanySize())

                // 모름 필드
                .unknownFields(planInput.getUnknownFields())
                .createdAt(planInput.getCreatedAt())
                .build();
    }
}
