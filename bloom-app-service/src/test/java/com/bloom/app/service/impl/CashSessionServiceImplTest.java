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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CashSessionServiceImplTest {
    private CashSessionRepository cashSessionRepository;
    private CashMovementRepository cashMovementRepository;
    private CashSessionMapper cashSessionMapper;
    private CashSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        cashSessionRepository = mock(CashSessionRepository.class);
        cashMovementRepository = mock(CashMovementRepository.class);
        cashSessionMapper = mock(CashSessionMapper.class);
        service = new CashSessionServiceImpl(
            cashSessionRepository,
            cashMovementRepository,
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
    void listsUnfilteredHistoryWithDeterministicSortAndOneRepositoryRead() {
        CashSession newer = CashSession.builder().id(8L).build();
        CashSession older = CashSession.builder().id(7L).build();
        CashSessionResponse newerResponse = CashSessionResponse.builder().id(8L).build();
        CashSessionResponse olderResponse = CashSessionResponse.builder().id(7L).build();
        when(cashSessionRepository.findAllHistory(any(Pageable.class))).thenReturn(
            new PageImpl<>(List.of(newer, older), PageRequest.of(2, 5), 12));
        when(cashSessionMapper.toResponse(newer)).thenReturn(newerResponse);
        when(cashSessionMapper.toResponse(older)).thenReturn(olderResponse);

        var result = service.getSessionHistory(
            null, PageRequest.of(2, 5, Sort.by("status")));

        assertThat(result.getContent()).containsExactly(newerResponse, olderResponse);
        verify(cashSessionRepository).findAllHistory(org.mockito.ArgumentMatchers.argThat(
            pageable -> pageable.getPageNumber() == 2
                && pageable.getPageSize() == 5
                && pageable.getSort().equals(Sort.by(
                    Sort.Order.desc("openedAt"), Sort.Order.desc("id")))));
        verify(cashSessionRepository, never()).findAllHistoryByStatus(any(), any());
        verifyNoInteractions(cashMovementRepository);
    }

    @Test
    void filtersHistoryBySupportedStatusWithoutReadingMovementLedger() {
        CashSession closed = CashSession.builder()
            .id(7L)
            .status(CashSessionStatus.CLOSED)
            .build();
        CashSessionResponse response = CashSessionResponse.builder()
            .id(7L)
            .status(CashSessionStatus.CLOSED)
            .build();
        when(cashSessionRepository.findAllHistoryByStatus(
            eq(CashSessionStatus.CLOSED), any(Pageable.class))).thenReturn(
                new PageImpl<>(List.of(closed), PageRequest.of(0, 20), 1));
        when(cashSessionMapper.toResponse(closed)).thenReturn(response);

        assertThat(service.getSessionHistory(
            CashSessionStatus.CLOSED, PageRequest.of(0, 20)).getContent())
            .containsExactly(response);

        verify(cashSessionRepository, never()).findAllHistory(any());
        verifyNoInteractions(cashMovementRepository);
    }

    @Test
    void returnsSuccessfulEmptyHistoryPageWithoutMappingWork() {
        when(cashSessionRepository.findAllHistory(any(Pageable.class))).thenReturn(
            new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = service.getSessionHistory(null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(cashSessionMapper, never()).toResponse(any());
        verifyNoInteractions(cashMovementRepository);
    }

    @Test
    void unknownSpecificSessionIdStillThrowsResourceNotFoundException() {
        when(cashSessionRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSessionDetails(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Cash session not found: 999");
    }
}
