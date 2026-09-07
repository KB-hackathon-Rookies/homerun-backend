package com.homerun.domain.notification.service;

import com.homerun.domain.notification.dto.request.RegisterDeviceTokenRequest;
import com.homerun.domain.notification.entity.DeviceToken;
import com.homerun.domain.notification.repository.DeviceTokenRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 디바이스 FCM 토큰 등록/갱신. 같은 토큰이 다시 오면 새 행 없이 소유자·마지막 사용 시각만 갱신한다. */
@Service
public class DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;
    private final Clock clock;

    public DeviceTokenService(DeviceTokenRepository deviceTokenRepository, Clock clock) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.clock = clock;
    }

    @Transactional
    public void register(Long memberId, RegisterDeviceTokenRequest request) {
        deviceTokenRepository
                .findByToken(request.token())
                .ifPresentOrElse(
                        existing -> existing.touch(memberId, request.platform(), clock.instant()),
                        () -> deviceTokenRepository.save(
                                new DeviceToken(memberId, request.token(), request.platform(), clock.instant())));
    }

    @Transactional
    public void unregister(Long memberId, String token) {
        deviceTokenRepository.deleteByMemberIdAndToken(memberId, token);
    }
}
