package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.service.mapper.CashMovementMapper;
import com.bloom.app.service.mapper.CashSessionMapper;
import com.bloom.app.service.util.CashReconciliationCalculator;
import com.bloom.app.service.util.CurrentActorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CashSessionServiceImplTest {
    private CashSessionRepository cashSessionRepository;
    private CashSessionMapper cashSessionMapper;
    private CashSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        cashSessionRepository = mock(CashSessionRepository.class);
        cashSessionMapper = mock(CashSessionMapper.class);
        service = new CashSessionServiceImpl(
            cashSessionRepository,
            mock(CashMovementRepository.class),
            mock(CashReconciliationCalculator.class),
            cashSessionMapper,
            mock(CashMovementMapper.class),
            mock(CurrentActorProvider.class)
        );
    }

    @Test
    void returnsMappedOpenSessionAsOptional() {
        CashSession session = CashSession.builder().id(7L).status(CashSessionStatus.OPEN).build();
        CashSessionResponse response = CashSessionResponse.builder()
            .id(7L)
            .status(CashSessionStatus.OPEN)
            .build();
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN))
            .thenReturn(Optional.of(session));
        when(cashSessionMapper.toResponse(session)).thenReturn(response);

        assertThat(service.getCurrentSession()).containsSame(response);
    }

    @Test
    void returnsEmptyOptionalWithoutResourceNotFoundExceptionWhenNoSessionIsOpen() {
        when(cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN))
            .thenReturn(Optional.empty());

        assertThat(service.getCurrentSession()).isEmpty();
        verify(cashSessionMapper, never()).toResponse(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void unknownSpecificSessionIdStillThrowsResourceNotFoundException() {
        when(cashSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionDetails(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Cash session not found: 999");
    }
}
