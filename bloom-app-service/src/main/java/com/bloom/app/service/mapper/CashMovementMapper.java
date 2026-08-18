package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.domain.model.CashMovement;
import org.springframework.stereotype.Component;

@Component
public class CashMovementMapper {
    public CashMovementResponse toResponse(CashMovement movement) {
        return CashMovementResponse.builder()
            .id(movement.getId())
            .movementType(movement.getMovementType())
            .sourceType(movement.getSourceType())
            .sourceId(movement.getSourceId())
            .referenceNo(movement.getReferenceNo())
            .amount(movement.getAmount())
            .direction(movement.getDirection())
            .occurredAt(movement.getOccurredAt())
            .actor(movement.getActor())
            .idempotencyKey(movement.getIdempotencyKey())
            .build();
    }
}
