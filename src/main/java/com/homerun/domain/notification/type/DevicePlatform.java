package com.homerun.domain.notification.type;

/** 디바이스 토큰이 어느 플랫폼에서 왔는지. FCM 페이로드 구성에는 영향이 없고 통계·관리용이다. */
public enum DevicePlatform {
    ANDROID,
    IOS,
    WEB
}
