package com.homerun.domain.contract.service;

import com.homerun.domain.contract.dto.ContractDtos.ContractProgress;
import com.homerun.domain.contract.dto.ContractDtos.StepView;
import com.homerun.domain.contract.entity.LeaseContract;
import com.homerun.domain.contract.type.ContractStep;
import com.homerun.domain.contract.type.StepStatus;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 계약 진행상태(PRP-02-04).
 *
 * <p>상태 컬럼을 따로 두지 않고 "끝난 날"에서 도출한다. 상태와 날짜를 둘 다 저장하면 둘이
 * 어긋났을 때 무엇이 맞는지 알 수 없다.
 *
 * <p>예정일은 보지 않는다. 잔금일을 적어 뒀다고 잔금을 낸 것은 아니다.
 */
@Component
class ContractProgressReader {

    ContractProgress read(LeaseContract contract) {
        Map<ContractStep, LocalDate> doneAt = new EnumMap<>(ContractStep.class);
        if (contract != null) {
            put(doneAt, ContractStep.CONTRACT_SIGNED, contract.getContractDate());
            // 계약금은 날짜가 아니라 금액 컬럼이다. 넣었다는 사실만 알 수 있고 언제인지는 모른다.
            if (contract.getDownPayment() != null && contract.getDownPayment() > 0) {
                doneAt.put(ContractStep.DOWN_PAYMENT, null);
            }
            put(doneAt, ContractStep.CONFIRMED_DATE, contract.getConfirmedDateAt());
            put(doneAt, ContractStep.LOAN_APPLIED, contract.getLoanAppliedAt());
            put(doneAt, ContractStep.BALANCE_PAID, contract.getBalancePaidAt());
        }

        ContractStep current = null;
        List<StepView> steps = new ArrayList<>();
        for (ContractStep step : ContractStep.values()) {
            boolean done = doneAt.containsKey(step);
            StepStatus status;
            if (done) {
                status = StepStatus.DONE;
            } else if (current == null) {
                // 끝나지 않은 것 중 첫 번째만 CURRENT 다. 앞 단계를 건너뛰고 뒤를 먼저 끝낸
                // 경우에도 남은 것 중 가장 앞을 다음 할 일로 본다.
                current = step;
                status = StepStatus.CURRENT;
            } else {
                status = StepStatus.PENDING;
            }
            steps.add(new StepView(step, step.label(), status, doneAt.get(step)));
        }
        return new ContractProgress(current, steps);
    }

    private void put(Map<ContractStep, LocalDate> doneAt, ContractStep step, LocalDate date) {
        if (date != null) {
            doneAt.put(step, date);
        }
    }
}
