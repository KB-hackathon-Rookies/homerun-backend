package com.homerun.global.external.fcm;

/** FCM 전송 실패. 리스너는 이 예외를 잡아 ACK 하지 않고, 재처리 리퍼가 재시도한다. */
public class FcmDeliveryException extends RuntimeException {

    public FcmDeliveryException(String message) {
        super(message);
    }

    public FcmDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
