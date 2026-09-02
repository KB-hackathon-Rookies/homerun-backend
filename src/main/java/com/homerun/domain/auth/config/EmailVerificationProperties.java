package com.homerun.domain.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.email-verification")
public record EmailVerificationProperties(
        String secret,
        String from,
        Duration codeTtl,
        Duration verifiedTokenTtl,
        Duration resendCooldown,
        Duration sendWindow,
        int maxSendsPerWindow,
        int maxAttempts) {

    public EmailVerificationProperties {
        codeTtl = codeTtl == null ? Duration.ofMinutes(5) : codeTtl;
        verifiedTokenTtl = verifiedTokenTtl == null ? Duration.ofMinutes(10) : verifiedTokenTtl;
        resendCooldown = resendCooldown == null ? Duration.ofMinutes(1) : resendCooldown;
        sendWindow = sendWindow == null ? Duration.ofMinutes(30) : sendWindow;
        maxSendsPerWindow = maxSendsPerWindow == 0 ? 5 : maxSendsPerWindow;
        maxAttempts = maxAttempts == 0 ? 5 : maxAttempts;
    }
}
