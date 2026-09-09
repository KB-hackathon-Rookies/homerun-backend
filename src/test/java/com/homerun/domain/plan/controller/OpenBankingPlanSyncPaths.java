package com.homerun.domain.plan.controller;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 두 동기화 경로의 매핑 유무를 확인하는 도구.
 *
 * <p>상태 코드로 매핑을 판단하지 않는다. 연결이 없는 회원의 진짜 동기화는
 * {@code OPEN_BANKING_NOT_CONNECTED} 로 404 를 주기 때문에, "404 가 아니면 매핑이 있다" 는
 * 여기서 성립하지 않는다. 등록된 경로 패턴을 직접 본다.
 */
final class OpenBankingPlanSyncPaths {

    /** 진짜 오픈뱅킹 동기화. 플래그와 무관하게 모든 환경에 있어야 한다. */
    static final String REAL_SYNC = "/api/v1/plans/{planId}/input/open-banking-sync";

    /** 데모 전용 페르소나 적재. 분리하면서도 이 주소를 바꾸지 않았다. */
    static final String MOCK = "/api/v1/plans/{planId}/input/open-banking-sync/mock";

    static Set<String> mappedPatterns(RequestMappingHandlerMapping mappings) {
        return mappings.getHandlerMethods().keySet().stream()
                .filter(info -> info.getPathPatternsCondition() != null)
                .flatMap(info -> info.getPathPatternsCondition().getPatternValues().stream())
                .collect(Collectors.toSet());
    }

    private OpenBankingPlanSyncPaths() {}
}
