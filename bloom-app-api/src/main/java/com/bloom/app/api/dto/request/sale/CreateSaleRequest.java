package com.bloom.app.api.dto.request.sale;

import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.domain.enums.PaymentType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateSaleRequest {
    @NotNull
    private BigDecimal discountAmount;

    @NotNull
    private BigDecimal paidAmount;

    private String description = "";
    @NotEmpty
    @Valid
    private List<CreateSaleItemRequest> saleItemList;

    @NotNull
    private PaymentType paymentType;
}
