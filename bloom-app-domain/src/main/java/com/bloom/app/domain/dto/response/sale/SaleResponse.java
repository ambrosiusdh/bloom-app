package com.bloom.app.domain.dto.response.sale;

import com.bloom.app.domain.dto.response.saleitem.SaleItemResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SaleResponse {
    private String code;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private String description;
    private Instant createdAt;
    private Instant updatedAt;
    private String createdBy;
    private String updatedBy;
    private List<SaleItemResponse> saleItems;
}
