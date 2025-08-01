package com.bloom.app.domain.dto.request.sale;

import com.bloom.app.domain.dto.request.saleitem.CreateSaleItemRequest;
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
    private BigDecimal discountAmount;

    private String description = "";
    private List<CreateSaleItemRequest> saleItemList;
}
