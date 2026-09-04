package com.homerun.domain.region.service;

import com.homerun.domain.region.entity.Region;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.region.type.PolicyArea;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PolicyRegionResolver {
    private final RegionRepository repository;

    public PolicyRegionResolver(RegionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PolicyArea resolve(Long regionId) {
        Set<Long> visited = new HashSet<>();
        Long current = regionId;
        while (current != null && visited.size() < 10 && visited.add(current)) {
            Region region = repository.findById(current).orElse(null);
            if (region == null) return null;
            if (region.getPolicyArea() != null) return region.getPolicyArea();
            current = region.getParentId();
        }
        // 누락·부모 순환·잘못된 계층은 비수도권이 아니라 미확인이다.
        return null;
    }
}
