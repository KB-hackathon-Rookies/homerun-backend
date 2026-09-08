package com.homerun.global.external.sms;

import com.homerun.global.exception.BusinessException;
import com.homerun.global.exception.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import net.nurigo.sdk.NurigoApp;
import net.nurigo.sdk.message.exception.NurigoMessageNotReceivedException;
import net.nurigo.sdk.message.model.FailedMessage;
import net.nurigo.sdk.message.model.Message;
import net.nurigo.sdk.message.service.DefaultMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(SolapiSmsSender.class);
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
        } catch (NurigoMessageNotReceivedException exception) {
            // 제공사가 건별로 거절한 경우다. 발신번호 미승인·잔액 부족·잘못된 수신번호가 여기서 갈린다.
            log.warn("[SMS] 발송 거절 to={} reasons={}", masked(recipient), reasonsOf(exception));
            throw new BusinessException(ErrorCode.SMS_DELIVERY_FAILED, exception);
        } catch (Exception exception) {
            // API 키 오류·네트워크 등 요청 자체가 실패한 경우. 예외 종류가 곧 원인이라 그대로 남긴다.
            log.warn("[SMS] 발송 실패 to={}", masked(recipient), exception);
            throw new BusinessException(ErrorCode.SMS_DELIVERY_FAILED, exception);
        }
    }

    /**
     * 거절 사유만 뽑는다.
     *
     * <p>{@link FailedMessage} 에는 수신·발신 번호도 들어 있지만 로그에 남기지 않는다. 원인을 아는 데
     * 필요한 것은 상태 코드와 문구뿐이다.
     */
    private String reasonsOf(NurigoMessageNotReceivedException exception) {
        List<FailedMessage> failed = exception.getFailedMessageList();
        if (failed == null || failed.isEmpty()) {
            return exception.getMessage();
        }
        return failed.stream()
                .map(message -> message.getStatusCode() + ":" + message.getStatusMessage())
                .collect(Collectors.joining(", "));
    }

    /**
     * 로그에 남길 번호. 가운데를 가린다.
     *
     * <p>어느 번호에서 실패했는지 구분은 돼야 하고, 휴대전화 번호를 로그에 그대로 쌓아 두지는 않는다.
     */
    static String masked(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
