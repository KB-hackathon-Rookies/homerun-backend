package com.homerun.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByProviderAndProviderUserId(AuthProvider provider, String providerId);
}
