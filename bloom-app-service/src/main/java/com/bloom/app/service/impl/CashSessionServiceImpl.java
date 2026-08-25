package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.cashsession.CloseCashSessionRequest;
import com.bloom.app.api.dto.request.cashsession.OpenCashSessionRequest;
import com.bloom.app.api.dto.response.cashsession.CashReconciliationResponse;
import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.service.CashSessionService;
import com.bloom.app.service.mapper.CashMovementMapper;
import com.bloom.app.service.mapper.CashSessionMapper;
import com.bloom.app.service.util.CashMoneyUtil;
import com.bloom.app.service.util.CashReconciliationCalculator;
import com.bloom.app.service.util.CashSessionLockConstants;
import com.bloom.app.service.util.CurrentActorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CashSessionServiceImpl implements CashSessionService {
    private final CashSessionRepository cashSessionRepository;
    private final CashMovementRepository cashMovementRepository;
    private final CashReconciliationCalculator reconciliationCalculator;
    private final CashSessionMapper cashSessionMapper;
    private final CashMovementMapper cashMovementMapper;
    private final CurrentActorProvider currentActorProvider;

    @Override
    @Transactional
    public CashSessionResponse openSession(OpenCashSessionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Open cash session request is required");
        }
        BigDecimal openingCash = CashMoneyUtil.requireNonNegative(
            request.getOpeningCash(), "Opening cash");

        cashSessionRepository.lockGlobalSessionTransition(
            CashSessionLockConstants.GLOBAL_SESSION_TRANSITION_LOCK_ID);
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
            return cashSessionMapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            if (containsConstraint(exception, "uq_cash_sessions_single_open")) {
                throw new CashSessionConflictException("A cash session is already open", exception);
            }
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CashSessionResponse> getCurrentSession() {
        return cashSessionRepository.findFirstByStatus(CashSessionStatus.OPEN)
            .map(cashSessionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CashSessionResponse getSessionDetails(Long sessionId) {
        return cashSessionMapper.toResponse(findSession(sessionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CashMovementResponse> getSessionMovements(
            Long sessionId, Pageable pageable) {
        findSession(sessionId);
        Pageable effectivePageable = PageRequest.of(
            pageable.getPageNumber(),
            pageable.getPageSize(),
            Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))
        );
        return cashMovementRepository.findBySessionId(sessionId, effectivePageable)
            .map(cashMovementMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public CashReconciliationResponse calculateExpectedCash(Long sessionId) {
        CashSession session = findSession(sessionId);
        CashReconciliationCalculator.Calculation calculation =
            reconciliationCalculator.calculate(session);
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
        BigDecimal actualClosingCash = CashMoneyUtil.requireNonNegative(
            request.getActualClosingCash(), "Actual closing cash");

        cashSessionRepository.lockGlobalSessionTransition(
            CashSessionLockConstants.GLOBAL_SESSION_TRANSITION_LOCK_ID);
        CashSession session = cashSessionRepository.findByIdForUpdate(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("Cash session not found: " + sessionId));
        if (session.getStatus() == CashSessionStatus.CLOSED) {
            throw new CashSessionConflictException("Cash session " + sessionId + " is already closed");
        }

        CashReconciliationCalculator.Calculation calculation =
            reconciliationCalculator.calculate(session);
        session.setExpectedClosingCash(calculation.expectedClosingCash());
        session.setActualClosingCash(actualClosingCash);
        session.setDifference(CashMoneyUtil.reconciliationBoundary(
            actualClosingCash.subtract(calculation.expectedClosingCash())));
        session.setClosedAt(Instant.now());
        session.setClosedBy(currentActorProvider.user());
        session.setStatus(CashSessionStatus.CLOSED);
        CashSession saved = cashSessionRepository.saveAndFlush(session);
        return cashSessionMapper.toResponse(saved);
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

    private boolean containsConstraint(Throwable exception, String constraintName) {
        Throwable current = exception;
        while (current != null) {
            if (current.getMessage() != null
                    && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
