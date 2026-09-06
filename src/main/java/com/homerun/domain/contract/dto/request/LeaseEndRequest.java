package com.homerun.domain.contract.dto.request;

import com.homerun.domain.contract.type.LeaseDecision;
import com.homerun.domain.contract.type.RenewalMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 갱신·퇴거 결정 저장(FR-H9-01·02). 계획당 1건이라 다시 저장하면 덮어쓴다.
 *
 * @param decision 갱신/퇴거/미정
 * @param renewalMethod 갱신 방법(갱신일 때). 청구권이면 청구권 사용으로 기록된다
 * @param noticeSentAt 갱신 의사 통보일
 */
@Schema(description = "갱신·퇴거 결정(FR-H9-01)")
public record LeaseEndRequest(@NotNull LeaseDecision decision, RenewalMethod renewalMethod, LocalDate noticeSentAt) {}
