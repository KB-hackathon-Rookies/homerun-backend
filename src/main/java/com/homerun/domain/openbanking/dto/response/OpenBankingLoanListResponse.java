package com.homerun.domain.openbanking.dto.response;

import java.time.Instant;
import java.util.List;

public record OpenBankingLoanListResponse(
        List<String> searchedBankCodes,
        int loanCount,
        int repaymentDetailUnavailableCount,
        List<OpenBankingLoanResponse> loans,
        Instant fetchedAt) {}
