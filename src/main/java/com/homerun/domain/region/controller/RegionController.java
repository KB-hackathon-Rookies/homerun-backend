package com.homerun.domain.region.controller;

import com.homerun.domain.region.dto.response.RegionOptionResponse;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@Tag(name = "희망 지역", description = "전세 사전 진단의 지역 선택값")
public class RegionController {
    private final RegionRepository repository;

    public RegionController(RegionRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/jeonse-options")
    @Operation(
            summary = "전세 희망지역 목록",
            description = "서울·인천·경기·그 외 지역. 반환한 id를 plan input의 regionId로 전달한다. 행정구역 코드나 id를 추측하지 않는다.")
    public ApiResponse<List<RegionOptionResponse>> options() {
        return ApiResponse.success(repository
                .findAllByCodeInOrderByCodeAsc(
                        List.of("JEONSE_SEOUL", "JEONSE_INCHEON", "JEONSE_GYEONGGI", "JEONSE_OTHER"))
                .stream()
                .map(r -> new RegionOptionResponse(r.getId(), r.getCode(), r.getName(), r.getPolicyArea()))
                .toList());
    }
}
