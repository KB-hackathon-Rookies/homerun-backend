package com.homerun.global.external.fcm;

import java.util.List;
import java.util.Map;

/**
 * FCM 푸시 전송 포트. 구현은 실제 Firebase({@link FirebaseFcmSender}) 또는 자격증명이 없을 때의
 * 무동작({@link NoOpFcmSender})이다.
 *
 * <p>도메인 엔티티가 아니라 원시 타입만 받는다. Firebase 의 {@code Notification} 타입과 우리
 * {@code Notification} 엔티티의 이름 충돌을 피하고, 전송 계층을 순수하게 유지한다.
 */
public interface FcmSender {

    /**
     * 여러 기기 토큰으로 같은 알림을 보낸다.
     *
     * @return 성공/실패 개수 요약
     * @throws RuntimeException 전송 자체가 실패(네트워크·인증 등)하면 던진다. 호출자는 이때 ACK 하지 않는다.
     */
    FcmSendResult send(List<String> tokens, String title, String body, Map<String, String> data);
}
