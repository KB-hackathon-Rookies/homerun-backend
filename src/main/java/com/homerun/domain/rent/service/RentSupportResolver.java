package com.homerun.domain.rent.service;

import com.homerun.domain.rent.model.RentSupportCombination;
import com.homerun.domain.rent.model.RentSupportOption;
import com.homerun.domain.rent.type.RentSupportType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 월세 지원금의 상호배타 관계를 풀고 유리한 조합을 고른다(POL-04-07, POL-04-08).
 *
 * <p>지원금은 단순히 더하면 안 된다. 동시에 받을 수 없는 조합이 있어서, 합산하면 실제로는
 * 받지 못할 금액을 받을 수 있다고 보여주게 된다.
 */
@Service
public class RentSupportResolver {

    /**
     * 동시에 받을 수 없는 짝.
     *
     * <p>FCT-092 주거급여 수급자는 청년월세 특별지원에서 제외된다. FCT-093 국토부 청년월세와
     * 지자체 월세지원은 동시 수령이 불가하다. 주거급여와 지자체 지원의 관계는 원문 근거가
     * 없어 배타로 두지 않는다.
     */
    private static final Map<RentSupportType, Set<RentSupportType>> EXCLUSIONS = new EnumMap<>(RentSupportType.class);

    static {
        exclude(RentSupportType.HOUSING_BENEFIT, RentSupportType.YOUTH_RENT_NATIONAL);
        exclude(RentSupportType.YOUTH_RENT_NATIONAL, RentSupportType.YOUTH_RENT_LOCAL);
    }

    private static void exclude(RentSupportType a, RentSupportType b) {
        EXCLUSIONS
                .computeIfAbsent(a, key -> EnumSet.noneOf(RentSupportType.class))
                .add(b);
        EXCLUSIONS
                .computeIfAbsent(b, key -> EnumSet.noneOf(RentSupportType.class))
                .add(a);
    }

    /** 두 지원금을 동시에 받을 수 있는가(POL-04-07). */
    public boolean conflicts(RentSupportType a, RentSupportType b) {
        return EXCLUSIONS.getOrDefault(a, Set.of()).contains(b);
    }

    /**
     * 수급 가능한 지원금들로 만들 수 있는 조합을 전부 구하고, 총액이 가장 큰 것을 추천한다
     * (POL-04-08).
     *
     * <p>지원금 종류가 적어 모든 부분집합을 훑어도 된다. 배타 관계가 그래프라서 단순히
     * 금액 순으로 고르면 최적이 아닐 수 있다.
     *
     * @return 총액 내림차순 조합 목록. 첫 항목이 추천 조합이다
     */
    public List<RentSupportCombination> resolve(List<RentSupportOption> eligible) {
        if (eligible.isEmpty()) {
            return List.of();
        }
        List<RentSupportCombination> combinations = new ArrayList<>();
        for (int mask = 1; mask < (1 << eligible.size()); mask++) {
            List<RentSupportOption> picked = select(eligible, mask);
            if (hasConflict(picked)) {
                continue;
            }
            long total =
                    picked.stream().mapToLong(RentSupportOption::totalAmount).sum();
            combinations.add(new RentSupportCombination(picked, total, false));
        }
        combinations.sort(
                Comparator.comparingLong(RentSupportCombination::totalAmount).reversed());

        List<RentSupportCombination> result = new ArrayList<>(combinations);
        result.set(0, result.get(0).asRecommended());
        return List.copyOf(result);
    }

    /** 추천 조합만 필요할 때. 받을 수 있는 게 없으면 빈 조합을 준다. */
    public RentSupportCombination recommend(List<RentSupportOption> eligible) {
        List<RentSupportCombination> combinations = resolve(eligible);
        return combinations.isEmpty() ? new RentSupportCombination(List.of(), 0, true) : combinations.get(0);
    }

    private List<RentSupportOption> select(List<RentSupportOption> all, int mask) {
        List<RentSupportOption> picked = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                picked.add(all.get(i));
            }
        }
        return picked;
    }

    private boolean hasConflict(List<RentSupportOption> picked) {
        for (int i = 0; i < picked.size(); i++) {
            for (int j = i + 1; j < picked.size(); j++) {
                if (conflicts(picked.get(i).type(), picked.get(j).type())) {
                    return true;
                }
            }
        }
        return false;
    }
}
