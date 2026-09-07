package com.homerun.global.external.fcm;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** 실제 Firebase Cloud Messaging 전송 구현. {@code fcm.enabled=true} 이고 자격증명이 있을 때만 빈으로 뜬다. */
public class FirebaseFcmSender implements FcmSender {

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseFcmSender(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public FcmSendResult send(List<String> tokens, String title, String body, Map<String, String> data) {
        if (tokens == null || tokens.isEmpty()) {
            return FcmSendResult.noRecipients();
        }
        MulticastMessage.Builder builder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(
                        Notification.builder().setTitle(title).setBody(body).build());
        if (data != null && !data.isEmpty()) {
            builder.putAllData(data);
        }
        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(builder.build());
            List<String> invalidTokens = IntStream.range(
                            0, response.getResponses().size())
                    .filter(index ->
                            isInvalidToken(response.getResponses().get(index).getException()))
                    .mapToObj(tokens::get)
                    .toList();
            return new FcmSendResult(response.getSuccessCount(), response.getFailureCount(), invalidTokens);
        } catch (FirebaseMessagingException exception) {
            // 전송 자체 실패. 호출자(리스너)가 ACK 하지 않고 리퍼가 재시도하도록 예외로 알린다.
            throw new FcmDeliveryException("FCM 멀티캐스트 전송 실패", exception);
        }
    }

    private boolean isInvalidToken(FirebaseMessagingException exception) {
        if (exception == null) {
            return false;
        }
        return exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
                || exception.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
