package com.homerun.domain.openbanking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 데모 전용 — 가입 직후 오픈뱅킹을 인가 없이 연결해 둔다.
 *
 * <p>{@code external-api.open-banking.mock-data=true} 일 때만 빈이 올라온다(운영·CI 는 없음).
 * 회원이 만들어지면 곧바로 목 연결을 세워, 사용자가 오픈뱅킹 화면에 들어오기 전에도 계좌·요약이
 * {@code MockDataOpenBankingClient} 샘플 데이터로 답하게 한다. 데모에서 "가입하면 이미 연결돼
 * 있다"를 만들어, 연동 화면이 인가·팝업 없이 연출만 하면 되게 한다.
 *
 * <p>시드 실패가 가입 자체를 막지는 않는다 — 예외를 삼킨다. 자동 연결은 데모 편의지 가입의
 * 전제가 아니고, 사용자가 나중에 오픈뱅킹 화면에서 다시 연결할 수 있다.
 */
@Component
@ConditionalOnProperty(prefix = "external-api.open-banking", name = "mock-data", havingValue = "true")
public class DemoOpenBankingSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoOpenBankingSeeder.class);

    private final OpenBankingService openBanking;

    public DemoOpenBankingSeeder(OpenBankingService openBanking) {
        this.openBanking = openBanking;
    }

    public void seed(Long memberId) {
        try {
            openBanking.mockConnect(memberId);
        } catch (RuntimeException exception) {
            log.warn("데모 오픈뱅킹 자동 연결 실패(memberId={}) — 가입은 계속한다", memberId, exception);
        }
    }
}
