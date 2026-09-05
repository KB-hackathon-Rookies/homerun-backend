package com.homerun.domain.alternative.service;

import com.homerun.domain.alternative.dto.request.AlternativeRecalculationRequest;
import com.homerun.domain.alternative.dto.response.AlternativeRecalculationResponse;
import com.homerun.domain.diagnosis.dto.response.DiagnosisResponse;
import com.homerun.domain.diagnosis.model.DiagnosisOverrides;
import com.homerun.domain.diagnosis.service.DiagnosisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대안 재계산(ALT-01-02). 보증금·독립일을 바꿔 보고 지금 계획과 나란히 비교한다.
 *
 * <p>계산은 한 줄도 하지 않는다 — {@link DiagnosisService} 를 두 번 부를 뿐이다. 같은 수치를 두
 * 곳에서 계산하면 반드시 갈라지기 때문에, 진단 공식은 DIA-02 한 곳에만 둔다.
 *
 * <p>기준선을 <b>저장된 진단이 아니라 같은 비용 가정으로 다시 계산한 값</b>으로 잡는다. 저장된
 * 진단과 비교하면 그때의 비용 가정 차이까지 섞여 들어와, 결과가 달라진 것이 축을 바꿔서인지 비용을
 * 다르게 넣어서인지 구분할 수 없다.
 *
 * <p>계획 자체는 건드리지 않는다. 대안을 실제로 채택하는 것은 계획 입력을 고치는 별개의 행동이다.
 */
@Service
public class AlternativeRecalculationService {

    private final DiagnosisService diagnosisService;

    public AlternativeRecalculationService(DiagnosisService diagnosisService) {
        this.diagnosisService = diagnosisService;
    }

    @Transactional(readOnly = true)
    public AlternativeRecalculationResponse recalculate(
            Long memberId, Long planId, AlternativeRecalculationRequest request) {
        DiagnosisOverrides overrides = new DiagnosisOverrides(request.hopeDeposit(), request.targetMoveDate());

        DiagnosisResponse current =
                diagnosisService.simulate(memberId, planId, request.assumptions(), DiagnosisOverrides.none());
        DiagnosisResponse alternative = overrides.isEmpty()
                ? current
                : diagnosisService.simulate(memberId, planId, request.assumptions(), overrides);

        return AlternativeRecalculationResponse.of(planId, current, alternative);
    }
}
