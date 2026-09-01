package com.homerun.global.external.realestate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RealEstateTransactionXmlParserTest {

    private final RealEstateTransactionXmlParser parser = new RealEstateTransactionXmlParser();

    @Test
    void parsesTransactionItemsAndMetadata() {
        String xml = """
                <response>
                  <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                  <body>
                    <items>
                      <item>
                        <dealAmount>47,000</dealAmount>
                        <excluUseAr>59.75</excluUseAr>
                        <umdNm>숭인동</umdNm>
                      </item>
                    </items>
                    <totalCount>1</totalCount>
                  </body>
                </response>
                """;

        RealEstateTransactionXmlParser.ParsedResponse response = parser.parse(xml);

        assertThat(response.resultCode()).isEqualTo("000");
        assertThat(response.resultMessage()).isEqualTo("OK");
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.get("dealAmount")).isEqualTo("47,000");
            assertThat(item.get("excluUseAr")).isEqualTo("59.75");
            assertThat(item.get("umdNm")).isEqualTo("숭인동");
        });
    }

    @Test
    void parsesEmptyItems() {
        String xml = """
                <response>
                  <header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                  <body><items/><totalCount>0</totalCount></body>
                </response>
                """;

        RealEstateTransactionXmlParser.ParsedResponse response = parser.parse(xml);

        assertThat(response.totalCount()).isZero();
        assertThat(response.items()).isEmpty();
    }
}
