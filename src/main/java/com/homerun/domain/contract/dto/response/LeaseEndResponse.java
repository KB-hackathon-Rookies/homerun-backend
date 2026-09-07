package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.entity.LeaseEnd;
import com.homerun.domain.contract.type.DepositReturnStatus;
import com.homerun.domain.contract.type.LeaseDecision;
import com.homerun.domain.contract.type.RenewalMethod;
import com.homerun.domain.contract.type.UnreturnedAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "갱신·퇴거 결정")
public record LeaseEndResponse(
        LeaseDecision decision,
        RenewalMethod renewalMethod,
        boolean claimRightUsed,
        LocalDate noticeSentAt,
        DepositReturnStatus depositReturned,
        Long returnAmountToBank,
        Long returnAmountToMe,
        UnreturnedAction unreturnedAction) {

    public static LeaseEndResponse from(LeaseEnd e) {
        return new LeaseEndResponse(
                e.getDecision(),
                e.getRenewalMethod(),
                e.isClaimRightUsed(),
                e.getNoticeSentAt(),
                e.getDepositReturned(),
                e.getReturnAmountToBank(),
                e.getReturnAmountToMe(),
                e.getUnreturnedAction());
    }
}
