package com.homerun.domain.fact;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

/**
 * 기준 수치를 읽는 유일한 통로.
 *
 * <p>정책 금액·한도·비율을 코드에 하드코딩하지 않는다. 매년 바뀌기 때문에 상수로 박으면
 * 다음 고시 때 어디를 고쳐야 하는지 아무도 모르게 된다.
 */
@Service
public class FactRegistry {

    private final ConfigEffectiveRepository repository;
    private final Clock clock;

    public FactRegistry(ConfigEffectiveRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 판정에 쓸 수 있는 수치를 가져온다.
     *
     * @throws BusinessException 레지스트리에 없거나 확정도가 CONFLICT·UNKNOWN·RETIRED 인 경우
     */
    public Fact require(String factCode) {
        ConfigEffective row = repository
                .findEffective(factCode, LocalDate.now(clock))
                .orElseThrow(() -> new BusinessException(ErrorCode.FACT_NOT_FOUND));

        if (!row.confidence().usable()) {
            throw new BusinessException(ErrorCode.UNUSABLE_FACT);
        }
        return new Fact(
                row.factCode(),
                row.item(),
                row.valueNum(),
                row.unit(),
                row.valueText(),
                row.sourceUrl(),
                row.confidence().provisional());
    }

    /** 금액 팩트를 원 단위로 읽는다. */
    public long won(String factCode) {
        return require(factCode).requireWon();
    }

    /** 확정되지 않은 수치인지만 확인한다. 판정을 건너뛸지 결정할 때 쓴다. */
    public boolean usable(String factCode) {
        return repository
                .findEffective(factCode, LocalDate.now(clock))
                .map(row -> row.confidence().usable())
                .orElse(false);
    }
}
