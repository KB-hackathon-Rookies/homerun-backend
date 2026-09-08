package com.homerun.domain.openbanking.repository;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.time.Duration;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * 오픈뱅킹 OAuth 의 state → memberId 를 Redis 에 잠깐 보관한다.
 *
 * <p>이전에는 HttpSession 에 담았는데, 앱이 {@code STATELESS} 이고 세션이 메모리라 재배포·재시작이나
 * 교차 사이트 콜백에서 값이 사라져 콜백이 {@code OPENBANKING_004} 로 실패했다. Redis 에 두면
 * 재시작에도 살아 있고, state(추측 불가한 무작위 값)만으로 콜백을 검증할 수 있어 세션 쿠키에
 * 의존하지 않는다.
 *
 * <p>{@link #consume(String)} 는 조회와 삭제를 원자적으로 처리해(GETDEL) 같은 state 를 두 번 쓰지
 * 못하게 막는다(재사용/CSRF 방지).
 */
@Repository
public class OpenBankingOAuthStateStore {

    // 오픈뱅킹 본인인증(공동인증서·휴대폰 등)이 몇 분 걸릴 수 있어 넉넉히 둔다.
    private static final Duration TTL = Duration.ofMinutes(10);
    private static final String KEY_PREFIX = "openbanking:oauth:state:";

    private final StringRedisTemplate redisTemplate;

    public OpenBankingOAuthStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** 연결 시작 시 state 와 그 요청을 낸 회원을 묶어 저장한다. */
    public void save(String state, Long memberId) {
        try {
            redisTemplate.opsForValue().set(key(state), String.valueOf(memberId), TTL);
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR, exception);
        }
    }

    /** 콜백에서 state 로 회원을 찾고 즉시 삭제한다(1회성). 없거나 만료됐으면 빈 값. */
    public Optional<Long> consume(String state) {
        try {
            String memberId = redisTemplate.opsForValue().getAndDelete(key(state));
            return memberId == null ? Optional.empty() : Optional.of(Long.valueOf(memberId));
        } catch (DataAccessException exception) {
            throw new BusinessException(ErrorCode.OPEN_BANKING_PROVIDER_ERROR, exception);
        }
    }

    private String key(String state) {
        return KEY_PREFIX + state;
    }
}
