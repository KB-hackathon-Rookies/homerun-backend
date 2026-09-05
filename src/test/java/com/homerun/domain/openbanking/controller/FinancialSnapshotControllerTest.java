package com.homerun.domain.openbanking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.homerun.domain.openbanking.dto.response.FinancialSnapshotResponse;
import com.homerun.domain.openbanking.service.FinancialSnapshotService;
import com.homerun.domain.openbanking.type.FinancialSnapshotSource;
import com.homerun.global.response.ApiResponse;
import com.homerun.global.security.principal.MemberPrincipal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class FinancialSnapshotControllerTest {

    @Test
    void should_returnAuthenticatedMembersLatestSnapshot() {
        FinancialSnapshotService service = Mockito.mock(FinancialSnapshotService.class);
        FinancialSnapshotResponse snapshot = new FinancialSnapshotResponse(
                1L,
                LocalDate.of(2026, 8, 31),
                FinancialSnapshotSource.OPEN_BANKING,
                false,
                10_000_000L,
                3_000_000L,
                null,
                null,
                250_000L,
                Instant.parse("2026-09-05T00:00:00Z"));
        when(service.latest(7L)).thenReturn(snapshot);
        FinancialSnapshotController controller = new FinancialSnapshotController(service);

        ApiResponse<FinancialSnapshotResponse> response = controller.latest(new MemberPrincipal(7L));

        assertThat(response.data()).isSameAs(snapshot);
        verify(service).latest(7L);
    }
}
