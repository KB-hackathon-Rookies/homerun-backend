package com.homerun.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 휴대전화 인증 설정. 이메일 인증({@code app.email-verification})과 같은 형태로 둔다.
 *
 * <p>전용 시크릿이 없으면 이메일 인증 시크릿을 쓴다. 둘 다 HMAC 서명 용도라 하나로 충분하다.
 *
 * <p>그 대체를 <b>여기서</b> 한다. YAML 에 {@code ${A:${B:}}} 로 적으면 안 된다 — Spring 의 기본값은
 * A 가 <b>없을 때만</b> 쓰이고, {@code A=} 처럼 빈 값으로 정의돼 있으면 그 빈 값이 그대로 이긴다.
 * {@code .env.example} 이 이 변수를 빈 채로 배포하므로 그대로 복사한 사람은 전원 시크릿이 빈 상태가
 * 되고, 휴대전화 인증번호 발송이 {@code AUTH_031} 로 500 을 낸다. 실제로 그렇게 터졌다.
 */
@ConfigurationProperties("app.phone-verification")
public record PhoneVerificationProperties(
        String secret,
        String fallbackSecret,
        Duration codeTtl,
        Duration verifiedTokenTtl,
        Duration resendCooldown,
        Duration sendWindow,
        int maxSendsPerWindow,
        int maxAttempts) {

    public PhoneVerificationProperties {
        secret = secret == null || secret.isBlank() ? fallbackSecret : secret;
        codeTtl = codeTtl == null ? Duration.ofMinutes(3) : codeTtl;
        verifiedTokenTtl = verifiedTokenTtl == null ? Duration.ofMinutes(10) : verifiedTokenTtl;
        resendCooldown = resendCooldown == null ? Duration.ofMinutes(1) : resendCooldown;
        sendWindow = sendWindow == null ? Duration.ofMinutes(30) : sendWindow;
        maxSendsPerWindow = maxSendsPerWindow == 0 ? 5 : maxSendsPerWindow;
        maxAttempts = maxAttempts == 0 ? 5 : maxAttempts;
    }
}
