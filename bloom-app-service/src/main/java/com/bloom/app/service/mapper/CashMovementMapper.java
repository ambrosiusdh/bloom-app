package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.cashsession.CashMovementResponse;
import com.bloom.app.domain.model.CashMovement;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CashMovementMapper {
    CashMovementResponse toResponse(CashMovement movement);
}
