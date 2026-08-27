package com.bloom.app.service.mapper;

import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CashSessionMapperTest {
    private final CashSessionMapper mapper = Mappers.getMapper(CashSessionMapper.class);

    @Test
    void mapsClosedSessionAuthoritativeReconciliationAndAuditFields() {
        Instant openedAt = Instant.parse("2026-08-20T01:02:03Z");
        Instant closedAt = Instant.parse("2026-08-20T09:10:11Z");
        CashSession session = CashSession.builder()
            .id(41L)
            .openingCash(new BigDecimal("100.0000"))
            .expectedClosingCash(new BigDecimal("140.2500"))
            .actualClosingCash(new BigDecimal("139.7500"))
            .difference(new BigDecimal("-0.5000"))
            .status(CashSessionStatus.CLOSED)
            .openedAt(openedAt)
            .openedBy(User.builder().username("alice").build())
            .closedAt(closedAt)
            .closedBy(User.builder().username("bob").build())
            .build();

        var response = mapper.toResponse(session);

        assertThat(response.getId()).isEqualTo(41L);
        assertThat(response.getOpeningCash()).isEqualByComparingTo("100.0000");
        assertThat(response.getExpectedClosingCash()).isEqualByComparingTo("140.2500");
        assertThat(response.getActualClosingCash()).isEqualByComparingTo("139.7500");
        assertThat(response.getDifference()).isEqualByComparingTo("-0.5000");
        assertThat(response.getStatus()).isEqualTo(CashSessionStatus.CLOSED);
        assertThat(response.getOpenedAt()).isEqualTo(openedAt);
        assertThat(response.getOpenedBy()).isEqualTo("alice");
        assertThat(response.getClosedAt()).isEqualTo(closedAt);
        assertThat(response.getClosedBy()).isEqualTo("bob");
    }

    @Test
    void leavesUnavailableOpenSessionClosingFieldsNull() {
        CashSession session = CashSession.builder()
            .id(42L)
            .openingCash(new BigDecimal("50.0000"))
            .expectedClosingCash(new BigDecimal("50.0000"))
            .status(CashSessionStatus.OPEN)
            .openedAt(Instant.parse("2026-08-21T01:02:03Z"))
            .openedBy(User.builder().username("alice").build())
            .build();

        var response = mapper.toResponse(session);

        assertThat(response.getActualClosingCash()).isNull();
        assertThat(response.getDifference()).isNull();
        assertThat(response.getClosedAt()).isNull();
        assertThat(response.getClosedBy()).isNull();
    }
}
