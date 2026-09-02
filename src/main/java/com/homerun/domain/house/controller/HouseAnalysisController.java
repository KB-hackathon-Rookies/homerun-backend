package com.homerun.domain.house.controller;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.service.HouseAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/houses")
@Tag(name = "집 통합조회", description = "선택한 주소를 건축물대장과 실거래가에 연결")
public class HouseAnalysisController {

    private final HouseAnalysisService service;

    public HouseAnalysisController(HouseAnalysisService service) {
        this.service = service;
    }

    @PostMapping("/analysis")
    @Operation(summary = "집 정보 통합조회", description = "주소 검색 결과를 기준으로 건축물 표제부·주택가격과 같은 달의 전월세 실거래가를 조회합니다.")
    public HouseAnalysisResponse analyze(@Valid @RequestBody HouseAnalysisRequest request) {
        return service.analyze(request);
    }
}
