package com.bloom.app.api.dto.response.stocktransfer;

import com.bloom.app.domain.model.UnitOfMeasure;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferLineResponse {
    private Long id;
    private Long itemId;
    private String itemSku;
    private String itemName;
    private BigDecimal quantity;
    private UnitOfMeasure unitOfMeasure;
}
