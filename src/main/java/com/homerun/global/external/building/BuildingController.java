package com.homerun.global.external.building;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/buildings")
@Tag(name = "건축물", description = "국토교통부 건축HUB 건축물대장 API")
public class BuildingController {

    private final BuildingLedgerService service;

    public BuildingController(BuildingLedgerService service) {
        this.service = service;
    }

    @GetMapping("/ledger")
    @Operation(summary = "건축물대장 조회", description = "선택한 주소의 표제부와 건축물대장 주택가격을 조회합니다.")
    public BuildingLedgerResponse findLedger(
            @Parameter(example = "1168010100") @RequestParam @Pattern(regexp = "\\d{10}") String legalDistrictCode,
            @RequestParam(defaultValue = "false") boolean mountain,
            @Parameter(example = "123") @RequestParam @Pattern(regexp = "\\d{1,4}") String mainLotNumber,
            @Parameter(example = "4") @RequestParam(defaultValue = "0") @Pattern(regexp = "\\d{1,4}")
                    String subLotNumber) {
        return service.findLedger(new BuildingLotQuery(legalDistrictCode, mountain, mainLotNumber, subLotNumber));
    }
}
