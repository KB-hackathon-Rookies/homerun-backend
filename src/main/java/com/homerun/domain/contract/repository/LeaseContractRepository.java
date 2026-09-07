package com.homerun.domain.contract.repository;

import com.homerun.domain.contract.entity.LeaseContract;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaseContractRepository extends JpaRepository<LeaseContract, Long> {

    Optional<LeaseContract> findByPlanId(Long planId);

    /** 잔금일이 주어진 날짜들 중 하나이고 아직 잔금을 치르지 않은 계약. 마감 임박 알림용. */
    List<LeaseContract> findByBalanceDateInAndBalancePaidAtIsNull(Collection<LocalDate> balanceDates);

    List<LeaseContract> findByBalancePaidAt(LocalDate balancePaidAt);

    List<LeaseContract> findByLeaseEndDateIn(Collection<LocalDate> leaseEndDates);
}
