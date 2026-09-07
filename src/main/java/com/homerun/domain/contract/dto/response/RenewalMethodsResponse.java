package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.type.RenewalMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * 갱신 방법 3종 비교(FR-H9-02).
 *
 * @param noticeWindow 갱신 의사 통보 시기(FCT-111)
 * @param methods 방법별 비교
 * @param claimRightAlreadyUsed 계약갱신청구권을 이미 사용했는가(저장된 갱신 결정 기준)
 * @param reuseNote 계약갱신청구권 재사용 관련 안내
 */
@Schema(description = "갱신 방법 3종 비교(FR-H9-02)")
public record RenewalMethodsResponse(
        String noticeWindow, List<Method> methods, boolean claimRightAlreadyUsed, String reuseNote) {

    public RenewalMethodsResponse {
        methods = List.copyOf(methods);
    }

    /**
     * @param method 갱신 방법
     * @param name 화면 이름
     * @param detail 인상 한도 등 핵심(법정 수치는 팩트 원문)
     * @param condition 성립 조건
     */
    public record Method(RenewalMethod method, String name, String detail, String condition) {}
}
