package com.homerun.global.external.realestate;

import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * DATA_GO_KR_SERVICE_KEY 가 없을 때 쓰는 목 전월세 실거래가.
 *
 * <p>건물명을 목 주소·목 건축물대장과 같은 {@code 홈런아파트} 로 맞춰 둔다. 이름이 어긋나면 매물
 * 분석에서 거래가 하나도 매칭되지 않아 전용면적이 수기 입력으로 떨어진다.
 */
@Component
@Primary
@ConditionalOnExpression("'${external-api.real-estate-transaction.service-key:}'.isBlank()")
public class MockRealEstateTransactionClient extends RealEstateTransactionClient {

    MockRealEstateTransactionClient(RealEstateTransactionProperties properties, RealEstateTransactionXmlParser parser) {
        super(properties, parser);
    }

    @Override
    public RealEstateTransactionResponse findTransactions(RealEstateTransactionRequest request) {
        List<Map<String, String>> items = List.of(
                row(request, "홈런아파트", "123-4", "44.55", "32,000", "0", "5"),
                row(request, "홈런아파트", "123-4", "59.92", "20,000", "60", "9"),
                row(request, "루키즈빌", "77-2", "38.10", "5,000", "75", "3"));
        return new RealEstateTransactionResponse(
                request.housingType(),
                request.legalDistrictCode(),
                request.dealYearMonth(),
                request.pageNo(),
                request.numOfRows(),
                items.size(),
                "00",
                "MOCK NORMAL SERVICE",
                items);
    }

    private Map<String, String> row(
            RealEstateTransactionRequest request,
            String buildingName,
            String jibun,
            String exclusiveArea,
            String deposit,
            String monthlyRent,
            String floor) {
        String yearMonth = request.dealYearMonth();
        return Map.of(
                "aptNm", buildingName,
                "offiNm", buildingName,
                "mhouseNm", buildingName,
                "jibun", jibun,
                "excluUseAr", exclusiveArea,
                "deposit", deposit,
                "monthlyRent", monthlyRent,
                "floor", floor,
                "dealYear", yearMonth.substring(0, 4),
                "dealMonth", yearMonth.substring(4));
    }
}
