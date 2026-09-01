package com.homerun.global.external.realestate;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;

@Component
public class RealEstateTransactionClient {

    private final RealEstateTransactionProperties properties;
    private final RealEstateTransactionXmlParser parser;
    private final RestClient restClient;

    public RealEstateTransactionClient(
            RealEstateTransactionProperties properties, RealEstateTransactionXmlParser parser) {
        this.properties = properties;
        this.parser = parser;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    public RealEstateTransactionResponse findTransactions(RealEstateTransactionRequest request) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new IllegalStateException("DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        }

        String endpoint = properties.endpointFor(request.housingType());
        try {
            String xml = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(endpoint)
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("LAWD_CD", request.legalDistrictCode())
                            .queryParam("DEAL_YMD", request.dealYearMonth())
                            .queryParam("pageNo", request.pageNo())
                            .queryParam("numOfRows", request.numOfRows())
                            .build())
                    .retrieve()
                    .onStatus(status -> status.isError(), (httpRequest, httpResponse) -> {
                        String errorBody = StreamUtils.copyToString(httpResponse.getBody(), StandardCharsets.UTF_8);
                        throw errorFromBody(errorBody);
                    })
                    .body(String.class);

            if (xml == null || xml.isBlank()) {
                throw new RealEstateTransactionUpstreamException("실거래가 API가 빈 응답을 반환했습니다.");
            }

            RealEstateTransactionXmlParser.ParsedResponse parsed = parser.parse(xml);
            if (!isSuccess(parsed.resultCode())) {
                throw new RealEstateTransactionUpstreamException("실거래가 API 요청이 실패했습니다: " + parsed.resultMessage());
            }

            return new RealEstateTransactionResponse(
                    request.housingType(),
                    request.legalDistrictCode(),
                    request.dealYearMonth(),
                    request.pageNo(),
                    request.numOfRows(),
                    parsed.totalCount(),
                    parsed.resultCode(),
                    parsed.resultMessage(),
                    parsed.items());
        } catch (RealEstateTransactionUpstreamException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new RealEstateTransactionUpstreamException("실거래가 API 호출에 실패했습니다.", exception);
        }
    }

    public RealEstateTransactionResponse findAllTransactions(
            HousingType housingType, String legalDistrictCode, String dealYearMonth) {
        int pageSize = 1000;
        RealEstateTransactionResponse firstPage = findTransactions(
                new RealEstateTransactionRequest(housingType, legalDistrictCode, dealYearMonth, 1, pageSize));
        if (firstPage.totalCount() <= pageSize) {
            return firstPage;
        }

        List<java.util.Map<String, String>> items = new ArrayList<>(firstPage.items());
        int pageCount = (int) Math.ceil((double) firstPage.totalCount() / pageSize);
        for (int pageNo = 2; pageNo <= pageCount; pageNo++) {
            items.addAll(findTransactions(new RealEstateTransactionRequest(
                            housingType, legalDistrictCode, dealYearMonth, pageNo, pageSize))
                    .items());
        }

        return new RealEstateTransactionResponse(
                housingType,
                legalDistrictCode,
                dealYearMonth,
                1,
                pageSize,
                firstPage.totalCount(),
                firstPage.resultCode(),
                firstPage.resultMessage(),
                List.copyOf(items));
    }

    private boolean isSuccess(String resultCode) {
        return "000".equals(resultCode) || "00".equals(resultCode);
    }

    private RealEstateTransactionUpstreamException errorFromBody(String errorBody) {
        try {
            RealEstateTransactionXmlParser.ParsedResponse parsed = parser.parse(errorBody);
            if (!parsed.resultMessage().isBlank()) {
                return new RealEstateTransactionUpstreamException("실거래가 API 요청이 실패했습니다: " + parsed.resultMessage());
            }
        } catch (RealEstateTransactionUpstreamException ignored) {
            // HTTP 오류 본문이 XML이 아니면 아래의 일반 메시지를 사용한다.
        }
        return new RealEstateTransactionUpstreamException("실거래가 API가 HTTP 오류를 반환했습니다.");
    }
}
