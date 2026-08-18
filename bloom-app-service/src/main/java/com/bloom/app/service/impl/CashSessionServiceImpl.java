package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.CashMovement;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.service.CashSessionService;
import com.bloom.app.service.mapper.CashSessionMapper;
import com.bloom.app.service.support.CashMoney;
import com.bloom.app.service.support.CashReconciliationCalculator;
import com.bloom.app.service.support.CurrentActorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CashSessionServiceImpl implements CashSessionService {
    private final CashSessionRepository cashSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashReconciliationCalculator reconciliationCalculator;
    private final CashSessionMapper cashSessionMapper;
    private final CurrentActorProvider currentActorProvider;

    @Override
    @Transactional
    public CashSessionResponse openSession(OpenCashSessionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Open cash session request is required");
        }
        BigDecimal openingCash = CashMoney.requireNonNegative(
            request.getOpeningCash(), "Opening cash");

        cashSessionRepository.lockGlobalSessionTransition();
        if (cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN).isPresent()) {
            throw new CashSessionConflictException("A cash session is already open");
        }

        CashSession session = CashSession.builder()
            .openedBy(currentActorProvider.user())
            .openingCash(openingCash)
            .expectedClosingCash(openingCash)
            .status(CashSessionStatus.OPEN)
            .openedAt(Instant.now())
            .build();
        try {
            CashSession saved = cashSessionRepository.saveAndFlush(session);
            return response(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new CashSessionConflictException("A cash session is already open", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionResponse getCurrentSession() {
        CashSession session = cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN)
            .orElseThrow(() -> new ResourceNotFoundException("No cash session is currently open"));
        return response(session);
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionResponse getSessionDetails(Long sessionId) {
        return response(findSession(sessionId));
    }

    @Override
    @Transactional
    public CashReconciliationResponse calculateExpectedCash(Long sessionId) {
        validateSessionId(sessionId);
        CashSession session = cashSessionRepository.findByIdForUpdate(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash session not found: " + sessionId));
        CashReconciliationCalculator.Calculation calculation =
            reconciliationCalculator.calculate(session);
        if (session.getStatus() == CashSessionStatus.OPEN
                && session.getExpectedClosingCash().compareTo(calculation.expectedClosingCash()) != 0) {
            session.setExpectedClosingCash(calculation.expectedClosingCash());
            cashSessionRepository.saveAndFlush(session);
        }
        return CashReconciliationResponse.builder()
            .sessionId(session.getId())
            .openingCash(session.getOpeningCash())
            .totalCashIn(calculation.totalCashIn())
            .totalCashOut(calculation.totalCashOut())
            .expectedClosingCash(calculation.expectedClosingCash())
            .build();
    }

    @Override
    @Transactional
    public CashSessionResponse closeSession(Long sessionId, CloseCashSessionRequest request) {
        validateSessionId(sessionId);
        if (request == null) {
            throw new IllegalArgumentException("Close cash session request is required");
        }
        BigDecimal actualClosingCash = CashMoney.requireNonNegative(
            request.getActualClosingCash(), "Actual closing cash");

        cashSessionRepository.lockGlobalSessionTransition();
        CashSession session = cashSessionRepository.findByIdForUpdate(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash session not found: " + sessionId));
        if (session.getStatus() == CashSessionStatus.CLOSED) {
            throw new CashSessionConflictException("Cash session " + sessionId + " is already closed");
        }

        CashReconciliationCalculator.Calculation calculation =
            reconciliationCalculator.calculate(session);
        session.setExpectedClosingCash(calculation.expectedClosingCash());
        session.setActualClosingCash(actualClosingCash);
        session.setDifference(CashMoney.reconciliationBoundary(
            actualClosingCash.subtract(calculation.expectedClosingCash())));
        session.setClosedAt(Instant.now());
        session.setClosedBy(currentActorProvider.user());
        session.setStatus(CashSessionStatus.CLOSED);
        CashSession saved = cashSessionRepository.saveAndFlush(session);
        return response(saved, calculation);
    }

    private CashSession findSession(Long sessionId) {
        validateSessionId(sessionId);
        return cashSessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash session not found: " + sessionId));
    }

    private void validateSessionId(Long sessionId) {
        if (sessionId == null || sessionId <= 0) {
            throw new IllegalArgumentException("Cash session ID must be positive");
        }
    }

    private CashSessionResponse response(CashSession session) {
        return response(session, reconciliationCalculator.calculate(session));
    }

    private CashSessionResponse response(
            CashSession session, CashReconciliationCalculator.Calculation calculation) {
        List<CashMovement> movements =
            cashMovementRepository.findBySessionIdOrderByOccurredAtAscIdAsc(session.getId());
        return cashSessionMapper.toResponse(session, calculation, movements);
    }
}
