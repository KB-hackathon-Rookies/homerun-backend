package com.homerun.domain.house.service;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.dto.response.RentTransactions;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.global.external.building.BuildingLedgerResponse;
import com.homerun.global.external.building.BuildingLedgerService;
import com.homerun.global.external.building.BuildingLotQuery;
import com.homerun.global.external.realestate.HousingType;
import com.homerun.global.external.realestate.RealEstateTransactionClient;
import com.homerun.global.external.realestate.RealEstateTransactionResponse;
import com.homerun.global.external.realestate.RealEstateTransactionUpstreamException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class HouseAnalysisService {

    private final BuildingLedgerService buildingLedgerService;
    private final RealEstateTransactionClient transactionClient;

    public HouseAnalysisService(
            BuildingLedgerService buildingLedgerService, RealEstateTransactionClient transactionClient) {
        this.buildingLedgerService = buildingLedgerService;
        this.transactionClient = transactionClient;
    }

    public HouseAnalysisResponse analyze(HouseAnalysisRequest request) {
        BuildingLotQuery lotQuery = request.toLotQuery();
        BuildingLedgerResponse ledger = buildingLedgerService.findLedger(lotQuery);
        List<String> warnings = new ArrayList<>();
        HouseType houseType = request.houseType() != null ? request.houseType() : resolveHouseType(ledger, warnings);
        String sigunguCode = request.legalDistrictCode().substring(0, 5);

        List<String> buildingNames = buildingNames(request, ledger);
        RentTransactions rents = fetchAndMatch(houseType, sigunguCode, request, buildingNames, warnings);
        return new HouseAnalysisResponse(request, houseType, ledger, rents, List.copyOf(warnings));
    }

    /**
     * 건축물대장 용도 문구로 주택 유형을 읽는다. 판별하지 못해도 실패시키지 않는다.
     *
     * <p>여기서 예외를 던지면 매물 등록 자체가 막힌다. 그런데 유형은 바로 다음 단계(STEP 2)에서
     * 사용자가 고르는 값이고, 유형별 실거래 API 가 없는 종류는 어차피 면적을 직접 받는다
     * (FR-P1-10). 못 읽었다고 등록을 거절할 이유가 없다 — {@link HouseType#OTHER} 로 두고
     * 경고만 남긴다.
     *
     * <p>순서가 있다. 다가구는 대장 주용도가 "단독주택" 이고 기타용도에 "다가구주택" 이 적히는
     * 일이 많아, 단독보다 먼저 본다.
     */
    private HouseType resolveHouseType(BuildingLedgerResponse ledger, List<String> warnings) {
        String description = ledger.titles().items().stream()
                .map(item ->
                        String.join(" ", value(item, "mainPurpsCdNm"), value(item, "etcPurps"), value(item, "bldNm")))
                .reduce("", (left, right) -> left + " " + right);
        if (description.contains("오피스텔")) {
            return HouseType.OFFICETEL;
        }
        if (description.contains("연립") || description.contains("다세대")) {
            return HouseType.VILLA;
        }
        if (description.contains("아파트")) {
            return HouseType.APARTMENT;
        }
        if (description.contains("다가구")) {
            return HouseType.MULTI_FAMILY;
        }
        if (description.contains("단독")) {
            return HouseType.DETACHED;
        }
        warnings.add(
                ledger.titles().items().isEmpty()
                        ? "이 주소의 건축물대장을 찾지 못해 주택 유형을 판별하지 못했습니다. 다음 단계에서 직접 골라 주세요."
                        : "건축물대장의 용도로는 주택 유형을 판별하지 못했습니다. 다음 단계에서 직접 골라 주세요.");
        return HouseType.OTHER;
    }

    private RentTransactions matchTransactions(
            RealEstateTransactionResponse source, HouseAnalysisRequest request, List<String> buildingNames) {
        String targetLot = lotNumber(request.mainLotNumber(), request.normalizedSubLotNumber());
        List<Map<String, String>> matches = source.items().stream()
                .filter(item -> lotMatches(item, targetLot) || buildingNameMatches(item, buildingNames))
                .toList();
        return new RentTransactions(true, source.totalCount(), matches.size(), null, matches);
    }

    private RentTransactions fetchAndMatch(
            HouseType houseType,
            String sigunguCode,
            HouseAnalysisRequest request,
            List<String> buildingNames,
            List<String> warnings) {
        // 단독·다가구는 유형별 실거래 API 가 없다(FR-P1-10). 억지로 조회하지 않고 면적을 직접
        // 입력받도록 넘긴다 — 없는 API 를 부르는 것보다 못 찾았다고 말하는 편이 정직하다.
        Optional<HousingType> queryable = houseType.toHousingType();
        if (queryable.isEmpty()) {
            warnings.add("이 주택 유형은 유형별 실거래가 조회가 제공되지 않아 전용면적을 직접 입력해야 합니다.");
            return new RentTransactions(false, 0, 0, null, List.of());
        }
        try {
            RealEstateTransactionResponse source =
                    transactionClient.findAllTransactions(queryable.get(), sigunguCode, request.dealYearMonth());
            return matchTransactions(source, request, buildingNames);
        } catch (RealEstateTransactionUpstreamException exception) {
            warnings.add("전월세 실거래가를 조회하지 못했습니다: " + exception.getMessage());
            return new RentTransactions(false, 0, 0, exception.getMessage(), List.of());
        }
    }

    private boolean lotMatches(Map<String, String> item, String targetLot) {
        return normalize(value(item, "jibun")).equals(normalize(targetLot));
    }

    private boolean buildingNameMatches(Map<String, String> item, List<String> buildingNames) {
        String sourceName = firstNonBlank(item, "aptNm", "offiNm", "mhouseNm", "buildingName");
        if (sourceName.isBlank()) {
            return false;
        }
        String normalizedSourceName = normalize(sourceName);
        return buildingNames.stream().map(this::normalize).anyMatch(normalizedSourceName::equals);
    }

    private List<String> buildingNames(HouseAnalysisRequest request, BuildingLedgerResponse ledger) {
        List<String> names = new ArrayList<>();
        if (request.buildingName() != null && !request.buildingName().isBlank()) {
            names.add(request.buildingName());
        }
        ledger.titles().items().stream()
                .map(item -> value(item, "bldNm"))
                .filter(name -> !name.isBlank())
                .forEach(names::add);
        return names.stream().filter(Objects::nonNull).distinct().toList();
    }

    private String firstNonBlank(Map<String, String> item, String... keys) {
        for (String key : keys) {
            String value = value(item, key);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String value(Map<String, String> item, String key) {
        return item.getOrDefault(key, "").trim();
    }

    /** 실거래·대장 항목의 {@code jibun} 과 맞춰 보려고 만드는 표시용 지번. 부번 0 은 붙이지 않는다. */
    private String lotNumber(String mainLotNumber, String subLotNumber) {
        String main = String.valueOf(Integer.parseInt(mainLotNumber));
        return "0".equals(subLotNumber) ? main : main + "-" + Integer.parseInt(subLotNumber);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s()-]", "").toLowerCase(Locale.ROOT);
    }
}
