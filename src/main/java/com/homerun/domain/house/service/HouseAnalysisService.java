package com.homerun.domain.house.service;

import com.homerun.domain.house.dto.request.HouseAnalysisRequest;
import com.homerun.domain.house.dto.response.HouseAnalysisResponse;
import com.homerun.domain.house.dto.response.RentTransactions;
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
        BuildingLotQuery lotQuery = new BuildingLotQuery(
                request.legalDistrictCode(),
                request.mountain(),
                request.mainLotNumber(),
                normalizeSubLotNumber(request.subLotNumber()));
        BuildingLedgerResponse ledger = buildingLedgerService.findLedger(lotQuery);
        HousingType housingType = request.housingType() != null ? request.housingType() : resolveHousingType(ledger);
        String sigunguCode = request.legalDistrictCode().substring(0, 5);

        List<String> buildingNames = buildingNames(request, ledger);
        List<String> warnings = new ArrayList<>();
        RentTransactions rents = fetchAndMatch(housingType, sigunguCode, request, buildingNames, warnings);
        return new HouseAnalysisResponse(request, housingType, ledger, rents, List.copyOf(warnings));
    }

    private HousingType resolveHousingType(BuildingLedgerResponse ledger) {
        String description = ledger.titles().items().stream()
                .map(item ->
                        String.join(" ", value(item, "mainPurpsCdNm"), value(item, "etcPurps"), value(item, "bldNm")))
                .reduce("", (left, right) -> left + " " + right);
        if (description.contains("오피스텔")) {
            return HousingType.OFFICETEL;
        }
        if (description.contains("연립") || description.contains("다세대")) {
            return HousingType.ROW_HOUSE;
        }
        if (description.contains("아파트")) {
            return HousingType.APARTMENT;
        }
        throw new IllegalArgumentException("건축물대장에서 주택 유형을 판별하지 못했습니다. housingType을 직접 입력해 주세요.");
    }

    private RentTransactions matchTransactions(
            RealEstateTransactionResponse source, HouseAnalysisRequest request, List<String> buildingNames) {
        String targetLot = lotNumber(request.mainLotNumber(), request.subLotNumber());
        List<Map<String, String>> matches = source.items().stream()
                .filter(item -> lotMatches(item, targetLot) || buildingNameMatches(item, buildingNames))
                .toList();
        return new RentTransactions(true, source.totalCount(), matches.size(), null, matches);
    }

    private RentTransactions fetchAndMatch(
            HousingType housingType,
            String sigunguCode,
            HouseAnalysisRequest request,
            List<String> buildingNames,
            List<String> warnings) {
        try {
            RealEstateTransactionResponse source =
                    transactionClient.findAllTransactions(housingType, sigunguCode, request.dealYearMonth());
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

    private String lotNumber(String mainLotNumber, String subLotNumber) {
        String main = String.valueOf(Integer.parseInt(mainLotNumber));
        String sub = normalizeSubLotNumber(subLotNumber);
        return "0".equals(sub) ? main : main + "-" + Integer.parseInt(sub);
    }

    private String normalizeSubLotNumber(String subLotNumber) {
        return subLotNumber == null || subLotNumber.isBlank() ? "0" : subLotNumber;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("[\\s()-]", "").toLowerCase(Locale.ROOT);
    }
}
