package com.homerun.domain.openbanking.repository;

import com.homerun.domain.openbanking.entity.OpenBankingConnection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OpenBankingConnectionRepository extends JpaRepository<OpenBankingConnection, Long> {

    Optional<OpenBankingConnection> findByMemberId(Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select connection from OpenBankingConnection connection where connection.memberId = :memberId")
    Optional<OpenBankingConnection> findByMemberIdForUpdate(@Param("memberId") Long memberId);
}
