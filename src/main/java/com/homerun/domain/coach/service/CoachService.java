package com.homerun.domain.coach.service;

import com.homerun.domain.coach.dto.request.CoachAskRequest;
import com.homerun.domain.coach.dto.response.CoachAskResponse;
import com.homerun.global.external.aicoach.AiCoachClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 코치 프록시.
 *
 * <p>ai-coach 는 자기가 받은 JWT 를 직접 검증해 {@code sub} 를 회원 식별자로 쓴다. Spring 이 토큰을
 * 새로 만들면 그 검증을 우회하는 셈이라, 호출자가 보낸 헤더를 그대로 넘긴다.
 */
@Service
public class CoachService {

    private static final Logger log = LoggerFactory.getLogger(CoachService.class);

    private final AiCoachClient aiCoachClient;

    public CoachService(AiCoachClient aiCoachClient) {
        this.aiCoachClient = aiCoachClient;
    }

    public CoachAskResponse ask(Long memberId, CoachAskRequest request, String authorization) {
        // 질문 원문은 남기지 않는다. 호출량과 단계만 있으면 사용 추이를 보는 데 충분하다.
        log.debug("AI 코치 질문 memberId={} stage={}", memberId, request.stage());
        return aiCoachClient.ask(request, authorization);
    }
}
