package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.cashsession.CashSessionResponse;
import com.bloom.app.domain.model.CashSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CashSessionMapper {
    @Mapping(target = "openedBy", source = "openedBy.username")
    @Mapping(target = "closedBy", source = "closedBy.username")
    CashSessionResponse toResponse(CashSession session);
}
