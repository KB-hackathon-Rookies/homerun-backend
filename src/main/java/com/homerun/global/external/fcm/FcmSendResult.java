package com.homerun.global.external.fcm;

import java.util.List;

/** FCM 멀티캐스트 전송 결과와 다시 사용할 수 없는 토큰 목록. */
public record FcmSendResult(int successCount, int failureCount, List<String> invalidTokens) {

    public FcmSendResult {
        invalidTokens = invalidTokens == null ? List.of() : List.copyOf(invalidTokens);
    }

    public FcmSendResult(int successCount, int failureCount) {
        this(successCount, failureCount, List.of());
    }

    public static FcmSendResult noRecipients() {
        return new FcmSendResult(0, 0, List.of());
    }

    public boolean hasFailure() {
        return failureCount > 0;
    }
}
