package com.homerun.domain.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 인증번호 발송 결과.
 *
 * <p>화면이 남은 시간을 세려면 두 가지가 필요하다. 인증번호가 언제 죽는지({@code expiresInSeconds})와,
 * 언제 다시 보낼 수 있는지({@code resendAvailableInSeconds})다. 이 값이 없으면 사용자는 만료된 번호를
 * 계속 넣어 보게 되고, 재발송을 눌러도 왜 막히는지 알 수 없다.
 *
 * <p>둘 다 서버 설정에서 나온다. 화면에 숫자를 박아 두면 설정을 바꿔도 화면만 거짓말을 한다.
 */
@Schema(description = "휴대전화 인증번호 발송 결과")
public record PhoneVerificationSendResponse(long expiresInSeconds, long resendAvailableInSeconds) {}
