package com.homerun.global.external.building;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class BuildingRegisterClient {

    private final BuildingRegisterProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public BuildingRegisterClient(BuildingRegisterProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).build();
    }

    public BuildingRegisterResponse findTitles(BuildingLotQuery query) {
        return request(properties.endpoints().title(), query);
    }

    public BuildingRegisterResponse findHousingPrices(BuildingLotQuery query) {
        return request(properties.endpoints().housingPrice(), query);
    }

    private BuildingRegisterResponse request(String endpoint, BuildingLotQuery query) {
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            throw new IllegalStateException("DATA_GO_KR_SERVICE_KEY가 설정되지 않았습니다.");
        }

        try {
            String json = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path(endpoint)
                            .queryParam("serviceKey", properties.serviceKey())
                            .queryParam("sigunguCd", query.sigunguCode())
                            .queryParam("bjdongCd", query.bjdongCode())
                            .queryParam("platGbCd", query.platGbCode())
                            .queryParam("bun", query.paddedMainLotNumber())
                            .queryParam("ji", query.paddedSubLotNumber())
                            .queryParam("numOfRows", 100)
                            .queryParam("pageNo", 1)
                            .queryParam("_type", "json")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (json == null || json.isBlank()) {
                throw new BuildingRegisterApiException("건축물대장 API가 빈 응답을 반환했습니다.");
            }
            return parse(json);
        } catch (BuildingRegisterApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BuildingRegisterApiException("건축물대장 API 호출에 실패했습니다.", exception);
        }
    }

    BuildingRegisterResponse parse(String json) {
        try {
            JsonNode response = objectMapper.readTree(json).path("response");
            JsonNode header = response.path("header");
            String resultCode = header.path("resultCode").asText();
            String resultMessage = header.path("resultMsg").asText();
            if (!"00".equals(resultCode) && !"000".equals(resultCode)) {
                throw new BuildingRegisterApiException("건축물대장 API 요청이 실패했습니다: " + resultMessage);
            }

            JsonNode body = response.path("body");
            List<Map<String, String>> items = new ArrayList<>();
            JsonNode itemNode = body.path("items").path("item");
            if (itemNode.isArray()) {
                itemNode.forEach(item -> items.add(toMap(item)));
            } else if (itemNode.isObject()) {
                items.add(toMap(itemNode));
            }

            return new BuildingRegisterResponse(
                    resultCode, resultMessage, body.path("totalCount").asInt(0), List.copyOf(items));
        } catch (BuildingRegisterApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BuildingRegisterApiException("건축물대장 API JSON 응답을 해석하지 못했습니다.", exception);
        }
    }

    private Map<String, String> toMap(JsonNode item) {
        Map<String, String> values = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = item.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            values.put(field.getKey(), field.getValue().asText("").trim());
        }
        return Map.copyOf(values);
    }
}
