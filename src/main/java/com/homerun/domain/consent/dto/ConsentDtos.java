package com.homerun.domain.consent.dto;

import com.homerun.domain.consent.type.ConsentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

/** 가구원 동의 관련 요청·응답. */
public final class ConsentDtos {

    private ConsentDtos() {}

    /**
     * 동의 링크 발급 요청.
     *
     * @param memberId 가구원 식별자
     * @param policyId 어떤 정책 때문에 필요한지
     * @param purpose 가구원에게 보여줄 목적 문구
     */
    @Schema(description = "동의 링크 발급 요청")
    public record IssueRequest(
            @NotNull Long memberId, Long policyId, @NotBlank String purpose) {}

    /**
     * 발급 결과.
     *
     * <p>{@code token} 은 이 응답에서만 볼 수 있다. 서버는 해시만 저장하므로 다시 조회할 수 없다.
     */
    @Schema(description = "동의 링크 발급 결과")
    public record IssueResponse(Long consentId, String token, Instant expiresAt) {}

    /**
     * 가구원에게 보여줄 화면.
     *
     * <p>이름이나 주민식별정보를 담지 않는다(SEC-01-05).
     */
    @Schema(description = "가구원 동의 화면")
    public record ConsentView(String relation, String purpose, Instant expiresAt) {}

    /** 가구원의 응답. */
    @Schema(description = "가구원 동의·거절")
    public record ResponseRequest(@NotNull Boolean agreed) {}

    /** 계획 단위 동의 현황 한 줄. */
    @Schema(description = "가구원별 동의 현황")
    public record MemberConsentStatus(
            Long memberId, String relation, boolean consentRequired, boolean verified, ConsentStatus status) {}

    /**
     * 동의 현황 전체.
     *
     * @param allVerified 확인이 필요한 가구원이 모두 끝났는가
     * @param members 가구원별 현황
     */
    @Schema(description = "계획 단위 동의 현황")
    public record ConsentOverview(boolean allVerified, List<MemberConsentStatus> members) {

        public ConsentOverview {
            members = List.copyOf(members);
        }
    }
}
