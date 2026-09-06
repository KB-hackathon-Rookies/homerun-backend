package com.homerun.domain.contract.dto.response;

import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.type.ContractCollateralMethod;
import com.homerun.domain.contract.type.LoanProductKind;
import com.homerun.domain.plan.type.HouseType;
import com.homerun.domain.plan.type.LeaseType;

/** 2루 최종 선택을 반영한 3루 계약 초안. */
public record ContractEntryResponse(
        Long contractId,
        int decisionRevision,
        Long propertyId,
        String propertyAddress,
        String bankName,
        String branchName,
        LeaseType leaseType,
        long deposit,
        LoanProductKind loanProductKind,
        ContractCollateralMethod collateralMethod,
        HouseType houseType) {

    public static ContractEntryResponse from(
            LeaseContract contract, int decisionRevision, String propertyAddress, String bankName, String branchName) {
        return new ContractEntryResponse(
                contract.getId(),
                decisionRevision,
                contract.getPropertyId(),
                propertyAddress,
                bankName,
                branchName,
                contract.getLeaseType(),
                contract.getDeposit(),
                contract.getLoanProductKind(),
                contract.getCollateralMethod(),
                contract.getHouseType());
    }
}
