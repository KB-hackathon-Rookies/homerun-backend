package com.homerun.domain.region.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.homerun.domain.region.entity.Region;
import com.homerun.domain.region.repository.RegionRepository;
import com.homerun.domain.region.type.PolicyArea;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PolicyRegionResolverTest {
    private final RegionRepository repository = mock(RegionRepository.class);
    private final PolicyRegionResolver resolver = new PolicyRegionResolver(repository);

    @Test
    void should_inheritParentPolicyArea() {
        Region child = mock(Region.class);
        Region parent = mock(Region.class);
        when(repository.findById(1L)).thenReturn(Optional.of(child));
        when(repository.findById(2L)).thenReturn(Optional.of(parent));
        when(child.getParentId()).thenReturn(2L);
        when(parent.getPolicyArea()).thenReturn(PolicyArea.SEOUL);
        assertThat(resolver.resolve(1L)).isEqualTo(PolicyArea.SEOUL);
    }

    @Test
    void should_returnUnknownForMissingOrUnclassifiedRegion() {
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve(99L)).isNull();
        Region region = mock(Region.class);
        when(region.getParentId()).thenReturn(null);
        when(repository.findById(1L)).thenReturn(Optional.of(region));
        assertThat(resolver.resolve(1L)).isNull();
    }

    @Test
    void should_stopWhenParentCycleExists() {
        Region region = mock(Region.class);
        when(region.getParentId()).thenReturn(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(region));
        assertThat(resolver.resolve(1L)).isNull();
        verify(repository, times(1)).findById(1L);
    }
}
