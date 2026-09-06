package com.homerun.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 휴대전화 인증 설정. 이메일 인증({@code app.email-verification})과 같은 형태로 둔다. */
@ConfigurationProperties("app.phone-verification")
public record PhoneVerificationProperties(
        String secret,
        Duration codeTtl,
        Duration verifiedTokenTtl,
        Duration resendCooldown,
        Duration sendWindow,
        int maxSendsPerWindow,
        int maxAttempts) {

    public PhoneVerificationProperties {
        codeTtl = codeTtl == null ? Duration.ofMinutes(3) : codeTtl;
        verifiedTokenTtl = verifiedTokenTtl == null ? Duration.ofMinutes(10) : verifiedTokenTtl;
        resendCooldown = resendCooldown == null ? Duration.ofMinutes(1) : resendCooldown;
        sendWindow = sendWindow == null ? Duration.ofMinutes(30) : sendWindow;
        maxSendsPerWindow = maxSendsPerWindow == 0 ? 5 : maxSendsPerWindow;
        maxAttempts = maxAttempts == 0 ? 5 : maxAttempts;
    }
}
