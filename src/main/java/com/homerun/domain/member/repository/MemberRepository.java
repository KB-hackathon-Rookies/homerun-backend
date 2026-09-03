package com.homerun.domain.member.repository;

import com.homerun.domain.auth.type.AuthProvider;
import com.homerun.domain.member.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByProviderAndProviderUserIdAndDeletedAtIsNull(AuthProvider provider, String providerId);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);
}
