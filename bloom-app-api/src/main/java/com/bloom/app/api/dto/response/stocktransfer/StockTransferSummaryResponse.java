package com.bloom.app.api.dto.response.stocktransfer;

import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferSummaryResponse {
    private Long id;
    private String code;
    private StockLocation sourceLocation;
    private StockLocation destinationLocation;
    private String description;
    private long lineCount;
    private String createdBy;
    private Instant createdAt;
}
