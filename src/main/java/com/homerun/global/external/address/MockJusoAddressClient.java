package com.homerun.global.external.address;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * JUSO_CONFIRM_KEY 가 없을 때 쓰는 목 주소 검색.
 *
 * <p>승인키는 신청해서 받아야 하는 값이라 로컬에 없는 경우가 흔하다. 키가 채워지면 이 빈은 조건에서
 * 빠지고 실제 {@link JusoAddressClient} 가 다시 유일한 후보가 된다.
 *
 * <p>건물명은 {@code 홈런아파트} 로 고정한다 — 목 실거래가와 이름이 맞아야 매물 분석에서 전용면적이
 * 자동으로 잡힌다.
 */
@Component
@Primary
@ConditionalOnExpression("'${external-api.juso.confirm-key:}'.isBlank()")
public class MockJusoAddressClient extends JusoAddressClient {

    private static final List<AddressSearchResult> FIXTURES = List.of(
            new AddressSearchResult(
                    "서울특별시 강남구 테헤란로 123",
                    "서울특별시 강남구 역삼동 123-4",
                    "06133",
                    "1168010100",
                    "116804166011",
                    "1168010100101230004000001",
                    "홈런아파트",
                    true,
                    false,
                    "123",
                    "4"),
            new AddressSearchResult(
                    "서울특별시 마포구 월드컵북로 396",
                    "서울특별시 마포구 상암동 1595",
                    "03925",
                    "1144012700",
                    "114404166024",
                    "1144012700115950000000001",
                    "홈런아파트",
                    true,
                    false,
                    "1595",
                    "0"),
            new AddressSearchResult(
                    "서울특별시 관악구 관악로 1",
                    "서울특별시 관악구 신림동 산56-1",
                    "08826",
                    "1162010100",
                    "116204154011",
                    "1162010100200560001000001",
                    "홈런아파트",
                    true,
                    true,
                    "56",
                    "1"));

    public MockJusoAddressClient(JusoProperties properties, ObjectMapper objectMapper) {
        super(properties, objectMapper);
    }

    @Override
    public AddressSearchResponse search(String keyword, int currentPage, int countPerPage) {
        List<AddressSearchResult> matched = FIXTURES.stream()
                .filter(result -> keyword == null
                        || keyword.isBlank()
                        || result.roadAddress().contains(keyword)
                        || result.jibunAddress().contains(keyword))
                .toList();
        // 아무 검색어로도 결과가 0 이면 화면이 막힌다. 목에서는 못 찾는 경우를 만들지 않는다.
        List<AddressSearchResult> addresses = matched.isEmpty() ? FIXTURES : matched;
        return new AddressSearchResponse(currentPage, countPerPage, addresses.size(), addresses);
    }
}
