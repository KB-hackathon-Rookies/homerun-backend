package com.homerun.domain.notification.service;

import static org.mockito.Mockito.verify;

import com.homerun.domain.notification.repository.DeviceTokenRepository;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository repository;

    @Test
    void should_unregisterOnlyOwnedToken() {
        new DeviceTokenService(repository, Clock.systemUTC()).unregister(7L, "device-token");

        verify(repository).deleteByMemberIdAndToken(7L, "device-token");
    }
}
