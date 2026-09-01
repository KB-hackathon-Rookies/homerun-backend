package com.homerun.global.external.address;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class JusoAddressClientTest {

    private final JusoAddressClient client =
            new JusoAddressClient(new JusoProperties("https://example.com", "test-key"), new ObjectMapper());

    @Test
    void parsesAddressSearchResult() {
        String json = """
                {
                  "results": {
                    "common": {
                      "errorCode": "0",
                      "errorMessage": "정상",
                      "currentPage": "1",
                      "countPerPage": "10",
                      "totalCount": "1"
                    },
                    "juso": [{
                      "roadAddr": "서울특별시 강남구 테헤란로 123",
                      "jibunAddr": "서울특별시 강남구 역삼동 123-4",
                      "zipNo": "06133",
                      "admCd": "1168010100",
                      "rnMgtSn": "116803122010",
                      "bdMgtSn": "1168010100101230004000001",
                      "bdNm": "홈런아파트",
                      "bdKdcd": "1",
                      "mtYn": "0",
                      "lnbrMnnm": "123",
                      "lnbrSlno": "4"
                    }]
                  }
                }
                """;

        AddressSearchResponse response = client.parse(json);

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.addresses()).singleElement().satisfies(address -> {
            assertThat(address.legalDistrictCode()).isEqualTo("1168010100");
            assertThat(address.mainLotNumber()).isEqualTo("123");
            assertThat(address.subLotNumber()).isEqualTo("4");
            assertThat(address.apartmentBuilding()).isTrue();
        });
    }
}
