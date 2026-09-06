package com.homerun.global.external.sms;

/**
 * 인증 문자 발송 추상화. 실제 발송은 SMS 제공사(Solapi 등) 구현체가 담당한다. 제공사·크리덴셜이
 * 준비되기 전에는 {@link StubSmsSender} 가 로그로 대체하므로, 개발·테스트가 발송 연동에 막히지 않는다.
 */
public interface SmsSender {

    /**
     * 인증번호를 문자로 보낸다.
     *
     * @param recipient 정규화된 수신 번호(숫자만, 예: 01012345678)
     * @param code 6자리 인증번호
     * @param expiresInMinutes 유효 시간(분)
     */
    void sendVerificationCode(String recipient, String code, long expiresInMinutes);
}
