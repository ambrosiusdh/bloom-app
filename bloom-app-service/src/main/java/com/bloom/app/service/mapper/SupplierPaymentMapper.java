package com.bloom.app.service.mapper;

import com.bloom.app.api.dto.response.supplierpayment.SupplierPaymentResponse;
import com.bloom.app.domain.model.SupplierPayment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SupplierPaymentMapper {
    @Mapping(source = "receipt.id", target = "receiptId")
    @Mapping(source = "receipt.code", target = "receiptCode")
    @Mapping(source = "supplier.id", target = "supplierId")
    @Mapping(source = "supplier.code", target = "supplierCode")
    @Mapping(source = "receipt.supplierNameSnapshot", target = "supplierName")
    @Mapping(source = "cashSession.id", target = "cashSessionId")
    SupplierPaymentResponse toResponse(SupplierPayment payment);
}
