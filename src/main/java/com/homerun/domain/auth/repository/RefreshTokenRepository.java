package com.homerun.domain.auth.repository;

import com.homerun.domain.auth.entity.RefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByMemberIdAndRevokedAtIsNull(Long memberId);
}
