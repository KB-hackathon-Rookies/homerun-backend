package com.homerun.global.external.address;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class JusoAddressClient {

    private final JusoProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public JusoAddressClient(JusoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    public AddressSearchResponse search(String keyword, int currentPage, int countPerPage) {
        if (properties.confirmKey() == null || properties.confirmKey().isBlank()) {
            throw new IllegalStateException("JUSO_CONFIRM_KEY가 설정되지 않았습니다.");
        }

        try {
            String json = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/addrLinkApi.do")
                            .queryParam("confmKey", properties.confirmKey())
                            .queryParam("currentPage", currentPage)
                            .queryParam("countPerPage", countPerPage)
                            .queryParam("keyword", keyword)
                            .queryParam("resultType", "json")
                            .queryParam("hstryYn", "Y")
                            .queryParam("addInfoYn", "Y")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new JusoApiException("주소 검색 API가 빈 응답을 반환했습니다.");
            }
            return parse(json);
        } catch (JusoApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JusoApiException("주소 검색 API 호출에 실패했습니다.", exception);
        }
    }

    AddressSearchResponse parse(String json) {
        try {
            JsonNode results = objectMapper.readTree(json).path("results");
            JsonNode common = results.path("common");
            String errorCode = common.path("errorCode").asText();
            if (!"0".equals(errorCode)) {
                throw new JusoApiException(
                        "주소 검색 API 요청이 실패했습니다: " + common.path("errorMessage").asText());
            }

            List<AddressSearchResult> addresses = new ArrayList<>();
            for (JsonNode address : results.path("juso")) {
                addresses.add(new AddressSearchResult(
                        text(address, "roadAddr"),
                        text(address, "jibunAddr"),
                        text(address, "zipNo"),
                        text(address, "admCd"),
                        text(address, "rnMgtSn"),
                        text(address, "bdMgtSn"),
                        text(address, "bdNm"),
                        "1".equals(text(address, "bdKdcd")),
                        "1".equals(text(address, "mtYn")),
                        text(address, "lnbrMnnm"),
                        text(address, "lnbrSlno")));
            }

            return new AddressSearchResponse(
                    integer(common, "currentPage"),
                    integer(common, "countPerPage"),
                    integer(common, "totalCount"),
                    List.copyOf(addresses));
        } catch (JusoApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new JusoApiException("주소 검색 API JSON 응답을 해석하지 못했습니다.", exception);
        }
    }

    private String text(JsonNode node, String fieldName) {
        return node.path(fieldName).asText("").trim();
    }

    private int integer(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        return value.isBlank() ? 0 : Integer.parseInt(value);
    }
}
