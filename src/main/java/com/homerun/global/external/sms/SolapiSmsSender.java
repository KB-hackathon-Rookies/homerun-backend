package com.homerun.global.external.sms;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

/**
 * Solapi(CoolSMS)를 통한 휴대전화 인증번호 실발송 구현체.
 *
 * <p>API 키·시크릿·승인된 발신번호가 모두 있는 배포 환경에서만 빈으로 등록된다. 하나라도 비어
 * 있으면 {@link StubSmsSender}가 대신 등록돼 로컬 개발과 테스트에서 문자 비용이 발생하지 않는다.
 */
@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${app.sms.solapi.api-key:}')"
        + " && T(org.springframework.util.StringUtils).hasText('${app.sms.solapi.api-secret:}')"
        + " && T(org.springframework.util.StringUtils).hasText('${app.sms.solapi.sender:}')")
public class SolapiSmsSender implements SmsSender {

    private static final String SOLAPI_BASE_URL = "https://api.solapi.com";

    private final DefaultMessageService messageService;
    private final String sender;

    public SolapiSmsSender(SolapiProperties properties) {
        this.messageService =
                NurigoApp.INSTANCE.initialize(properties.apiKey(), properties.apiSecret(), SOLAPI_BASE_URL);
        this.sender = properties.sender();
    }

    @Override
    public void sendVerificationCode(String recipient, String code, long expiresInMinutes) {
        Message message = new Message();
        message.setFrom(sender);
        message.setTo(recipient);
        message.setText("[홈런] 인증번호 [" + code + "]를 " + expiresInMinutes + "분 안에 입력해 주세요.");
        try {
            messageService.send(message);
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.SMS_DELIVERY_FAILED, exception);
        }
    }
}
