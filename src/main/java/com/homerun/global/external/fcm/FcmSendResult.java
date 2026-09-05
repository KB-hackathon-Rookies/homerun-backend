package com.homerun.global.external.fcm;

/** FCM 멀티캐스트 전송 결과 요약. 개별 토큰 정리(무효 토큰 삭제)는 후속 과제. */
public record FcmSendResult(int successCount, int failureCount) {

    public static FcmSendResult noRecipients() {
        return new FcmSendResult(0, 0);
    }

    public boolean hasFailure() {
        return failureCount > 0;
    }
}
