package com.homerun.domain.document.dto;

import com.homerun.domain.document.type.DocumentHoldingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/** 서류 보유 상태와 유효기간(EVI-01-04 · EVI-01-07). */
public final class HoldingDtos {

    private HoldingDtos() {}

    /**
     * 서류 상태 기록.
     *
     * @param status 사용자가 고르는 상태. {@code EXPIRED} 는 판정 결과라 못 고른다
     * @param issuedAt 발급일. 인정 기간이 있는 서류는 이 날짜로 만료일이 정해진다
     */
    @Schema(description = "서류 상태 기록")
    public record RecordRequest(
            @NotBlank(message = "서류 코드가 필요하다") String documentCode,
            @NotNull(message = "상태가 필요하다") DocumentHoldingStatus status,
            LocalDate issuedAt) {}

    /**
     * 서류 한 건의 보유 현황.
     *
     * @param expiresAt 인정 기간을 아는 서류만 값이 있다
     * @param daysUntilExpiry 만료까지 남은 날. 만료됐으면 음수, 모르면 null
     * @param validityNote 인정 기간을 모를 때의 안내
     * @param action 지금 해야 할 일. 없으면 null
     */
    @Schema(description = "서류 보유 현황")
    public record HoldingView(
            String documentCode,
            String documentName,
            DocumentHoldingStatus status,
            String statusLabel,
            LocalDate issuedAt,
            LocalDate expiresAt,
            Long daysUntilExpiry,
            boolean expiringSoon,
            String validityNote,
            String action) {}

    /**
     * 계획 하나의 서류 현황 전체.
     *
     * @param needsReissue 다시 떼야 하는 서류. 비어 있으면 급한 것이 없다
     * @param expiringSoon 곧 만료되는 서류
     */
    @Schema(description = "서류 현황")
    public record HoldingList(List<HoldingView> documents, List<String> needsReissue, List<String> expiringSoon) {

        public HoldingList {
            documents = List.copyOf(documents);
            needsReissue = List.copyOf(needsReissue);
            expiringSoon = List.copyOf(expiringSoon);
        }
    }
}
