package com.homerun.domain.contract.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 대출 승계·이전 안내(FR-H10-03). 새 집으로 대출을 이어가는 방법과, 은행마다 다르니 문의하라는
 * 안내를 준다.
 *
 * @param methods 대출을 이어가는 방법
 * @param note 은행별 차이·문의 안내
 */
@Schema(description = "대출 승계·이전 안내(FR-H10-03)")
public record LoanTransferGuideResponse(List<Method> methods, String note) {

    public LoanTransferGuideResponse {
        methods = List.copyOf(methods);
    }

    public record Method(String name, String detail) {}
}
