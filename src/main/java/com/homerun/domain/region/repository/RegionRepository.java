package com.homerun.domain.region.repository;

import com.homerun.domain.region.entity.Region;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegionRepository extends JpaRepository<Region, Long> {
    List<Region> findAllByCodeInOrderByCodeAsc(Collection<String> codes);
}
