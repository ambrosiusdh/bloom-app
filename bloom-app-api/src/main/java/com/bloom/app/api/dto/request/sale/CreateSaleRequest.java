package com.bloom.app.api.dto.request.sale;

import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.domain.enums.PaymentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    @Digits(integer = 15, fraction = 4, message = "Discount amount must fit NUMERIC(19,4)")
    private BigDecimal discountAmount;

    @NotNull
    @Digits(integer = 15, fraction = 4, message = "Paid amount must fit NUMERIC(19,4)")
    private BigDecimal paidAmount;

    private String description = "";
    @NotEmpty
    @Valid
    private List<CreateSaleItemRequest> saleItemList;

    @NotNull
    private PaymentType paymentType;
}
