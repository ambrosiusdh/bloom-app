package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.domain.enums.CashMovementDirection;
import com.bloom.app.domain.enums.CashMovementSourceType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.exception.CashMovementIdempotencyConflictException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.CashMovement;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.persistence.repository.CashMovementRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.command.RecordCashMovementCommand;
import com.bloom.app.service.mapper.CashMovementMapper;
import com.bloom.app.service.support.CashMoney;
import com.bloom.app.service.support.CashReconciliationCalculator;
import com.bloom.app.service.support.CurrentActorProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CashMovementServiceImpl implements CashMovementService {
    private static final int MAX_KEY_LENGTH = 100;

    private final CashMovementRepository cashMovementRepository;
    private final CashSessionRepository cashSessionRepository;
    private final CashReconciliationCalculator reconciliationCalculator;
    private final CashMovementMapper cashMovementMapper;
    private final CurrentActorProvider currentActorProvider;

    @Override
    @Transactional
    public CashMovementResponse recordMovement(RecordCashMovementCommand command) {
        PreparedMovement prepared = validate(command);

        if (prepared.idempotencyKey() != null) {
            cashMovementRepository.lockIdempotencyKey(prepared.idempotencyKey());
            CashMovement existing = cashMovementRepository
                .findByIdempotencyKey(prepared.idempotencyKey())
                .orElse(null);
            if (existing != null) {
                if (!sameMovement(existing, prepared)) {
                    throw new CashMovementIdempotencyConflictException();
                }
                return cashMovementMapper.toResponse(existing);
            }
        }

        CashSession session = cashSessionRepository.findByIdForUpdate(prepared.sessionId())
            .orElseThrow(() -> new ResourceNotFoundException(
                "Cash session not found: " + prepared.sessionId()));
        if (session.getStatus() != CashSessionStatus.OPEN) {
            throw new CashSessionConflictException(
                "Cash session " + session.getId() + " is closed and rejects new cash movements");
        }

        CashMovement movement = CashMovement.builder()
            .session(session)
            .movementType(prepared.command().movementType())
            .sourceType(prepared.command().sourceType())
            .sourceId(prepared.command().sourceId())
            .referenceNo(prepared.referenceNo())
            .amount(prepared.amount())
            .direction(prepared.command().direction())
            .actor(currentActorProvider.username())
            .idempotencyKey(prepared.idempotencyKey())
            .build();
        CashMovement saved = cashMovementRepository.saveAndFlush(movement);

        session.setExpectedClosingCash(
            reconciliationCalculator.calculate(session).expectedClosingCash());
        cashSessionRepository.saveAndFlush(session);
        return cashMovementMapper.toResponse(saved);
    }

    private PreparedMovement validate(RecordCashMovementCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Cash movement command is required");
        }
        if (command.sessionId() == null || command.sessionId() <= 0) {
            throw new IllegalArgumentException("Cash session ID must be positive");
        }
        if (command.movementType() == null) {
            throw new IllegalArgumentException("Cash movement type is required");
        }
        if (command.sourceType() == null) {
            throw new IllegalArgumentException("Cash movement source type is required");
        }
        if (command.sourceId() == null || command.sourceId() <= 0) {
            throw new IllegalArgumentException("Cash movement source ID must be positive");
        }
        if (command.direction() == null) {
            throw new IllegalArgumentException("Cash movement direction is required");
        }
        validateApprovedSemantics(command);
        String reference = normalizeRequired(command.referenceNo(), "Cash movement reference");
        String idempotencyKey = normalizeOptional(command.idempotencyKey(), "Idempotency key");
        BigDecimal amount = CashMoney.requirePositive(command.amount(), "Cash movement amount");
        return new PreparedMovement(command, command.sessionId(), reference, amount, idempotencyKey);
    }

    private void validateApprovedSemantics(RecordCashMovementCommand command) {
        boolean valid = switch (command.movementType()) {
            case SALE_PAYMENT -> command.sourceType()
                == CashMovementSourceType.SALE
                && command.direction() == CashMovementDirection.IN;
            case SUPPLIER_PAYMENT -> command.sourceType()
                == CashMovementSourceType.SUPPLIER_PAYMENT
                && command.direction() == CashMovementDirection.OUT;
            case EXPENSE -> command.sourceType()
                == CashMovementSourceType.EXPENSE
                && command.direction() == CashMovementDirection.OUT;
        };
        if (!valid) {
            throw new IllegalArgumentException(
                "Cash movement type, source type, and direction are inconsistent");
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must not exceed 100 characters");
        }
        return normalized;
    }

    private String normalizeOptional(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_KEY_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must not exceed 100 characters");
        }
        return normalized;
    }

    private boolean sameMovement(CashMovement existing, PreparedMovement prepared) {
        RecordCashMovementCommand command = prepared.command();
        return existing.getSession().getId().equals(prepared.sessionId())
            && existing.getMovementType() == command.movementType()
            && existing.getSourceType() == command.sourceType()
            && existing.getSourceId().equals(command.sourceId())
            && existing.getReferenceNo().equals(prepared.referenceNo())
            && existing.getAmount().compareTo(prepared.amount()) == 0
            && existing.getDirection() == command.direction();
    }

    private record PreparedMovement(
        RecordCashMovementCommand command,
        Long sessionId,
        String referenceNo,
        BigDecimal amount,
        String idempotencyKey
    ) {
    }
}
