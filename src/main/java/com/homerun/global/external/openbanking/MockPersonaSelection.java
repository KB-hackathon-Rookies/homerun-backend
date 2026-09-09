package com.homerun.global.external.openbanking;

import com.homerun.domain.openbanking.type.Persona;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 데모에서 어떤 페르소나로 답할지 정한다.
 *
 * <p>{@link OpenBankingClient} 조회 메서드에는 회원 식별자가 없다. 오픈뱅킹 원문 규격이
 * 사용자일련번호와 핀테크이용번호만 넘기게 되어 있어서다. 그래서 회원별 선택을 여기 한 곳에 모아
 * 두고, 목 클라이언트는 사용자일련번호로 되짚어 읽는다.
 *
 * <p>기본값은 {@code KIM_KUKMIN}(김국민)다. 시연 대본이 "독립을 준비하는 평범한 청년" 한 명으로
 * 흐르기 때문에, 페르소나를 고른 적 없는 회원 — 가짜 오픈뱅킹 연결만 한 회원 포함 — 은 전부
 * 김국민으로 보여야 한다. 다른 페르소나는 {@code POST /api/v1/plans/{planId}/input/open-banking-sync/mock}
 * 로 명시적으로 실었을 때만 나온다.
 *
 * <p>선택은 메모리에만 둔다. 데모용이라 재시작하면 기본값으로 돌아가는 편이 오히려 안전하고,
 * 컬럼·마이그레이션을 늘리면 사업자 등록 후 이 데모 코드를 걷어낼 때 되돌릴 것이 늘어난다.
 */
@Component
@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")
public class MockPersonaSelection {

    /**
     * 가짜 연결이 만드는 사용자일련번호의 접두사. {@code OpenBankingService.mockConnect} 가 같은
     * 상수를 쓴다 — 양쪽이 따로 문자열을 들고 있으면 연결은 되는데 페르소나만 기본값으로 떨어지는,
     * 화면에서는 티가 안 나는 어긋남이 생긴다.
     */
    public static final String MOCK_USER_SEQ_NO_PREFIX = "MOCK-";

    private final Persona defaultPersona;
    private final Map<Long, Persona> selectedByMember = new ConcurrentHashMap<>();

    public MockPersonaSelection(@Value("${external-api.open-banking.mock-persona:KIM_KUKMIN}") Persona defaultPersona) {
        this.defaultPersona = defaultPersona;
    }

    /** 페르소나 적재 경로가 회원의 선택을 알려 준다. 이후 조회는 전부 이 페르소나로 답한다. */
    public void select(Long memberId, Persona persona) {
        if (memberId == null || persona == null) {
            return;
        }
        selectedByMember.put(memberId, persona);
    }

    /**
     * 실제 인가로 받은 사용자일련번호는 접두사가 없어 회원을 되짚을 수 없다. 그때는 기본 페르소나로
     * 답한다 — 데모 화면이 비어 보이는 것보다 김국민으로 일관되게 보이는 편이 낫다.
     */
    public Persona resolve(String userSeqNo) {
        Long memberId = memberId(userSeqNo);
        return memberId == null ? defaultPersona : selectedByMember.getOrDefault(memberId, defaultPersona);
    }

    public Persona defaultPersona() {
        return defaultPersona;
    }

    private Long memberId(String userSeqNo) {
        if (userSeqNo == null || !userSeqNo.startsWith(MOCK_USER_SEQ_NO_PREFIX)) {
            return null;
        }
        try {
            return Long.valueOf(userSeqNo.substring(MOCK_USER_SEQ_NO_PREFIX.length()));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
