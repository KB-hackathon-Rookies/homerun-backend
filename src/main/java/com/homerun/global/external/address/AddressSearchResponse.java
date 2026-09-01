package com.homerun.global.external.address;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "도로명주소 검색 응답")
public record AddressSearchResponse(
        int currentPage, int countPerPage, int totalCount, List<AddressSearchResult> addresses) {}
