package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.model.CashSession;
import org.springframework.stereotype.Component;

@Component
public class CashSessionMapper {
    public CashSessionResponse toResponse(CashSession session) {
        return CashSessionResponse.builder()
            .id(session.getId())
            .openingCash(session.getOpeningCash())
            .expectedClosingCash(session.getExpectedClosingCash())
            .actualClosingCash(session.getActualClosingCash())
            .difference(session.getDifference())
            .openedAt(session.getOpenedAt())
            .openedBy(session.getOpenedBy().getUsername())
            .closedAt(session.getClosedAt())
            .closedBy(session.getClosedBy() == null ? null : session.getClosedBy().getUsername())
            .status(session.getStatus())
            .version(session.getVersion())
            .build();
    }
}
