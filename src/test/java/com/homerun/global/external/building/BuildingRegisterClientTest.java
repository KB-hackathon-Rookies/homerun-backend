package com.homerun.global.external.building;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class BuildingRegisterClientTest {

    private final BuildingRegisterClient client = new BuildingRegisterClient(
            new BuildingRegisterProperties(
                    "https://example.com", "test-key", new BuildingRegisterProperties.Endpoints("/title", "/price")),
            new ObjectMapper());

    @Test
    void parsesBuildingRegisterItems() {
        String json = """
                {
                  "response": {
                    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
                    "body": {
                      "items": {"item": [{
                        "bldNm": "홈런아파트",
                        "mainPurpsCdNm": "공동주택(아파트)",
                        "useAprDay": "20180321"
                      }]},
                      "totalCount": 1
                    }
                  }
                }
                """;

        BuildingRegisterResponse response = client.parse(json);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.get("bldNm")).isEqualTo("홈런아파트");
            assertThat(item.get("mainPurpsCdNm")).contains("아파트");
        });
    }
}
