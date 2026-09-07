package com.homerun.domain.notification.repository;

import com.homerun.domain.notification.entity.DeviceToken;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    Optional<DeviceToken> findByToken(String token);

    List<DeviceToken> findByMemberId(Long memberId);

    long deleteByMemberIdAndToken(Long memberId, String token);

    long deleteByTokenIn(Collection<String> tokens);
}
