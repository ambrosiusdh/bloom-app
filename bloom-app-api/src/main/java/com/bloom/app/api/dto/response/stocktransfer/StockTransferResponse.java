package com.bloom.app.api.dto.response.stocktransfer;

import com.bloom.app.domain.enums.StockLocation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockTransferResponse {
    private Long id;
    private String code;
    private String requestKey;
    private StockLocation sourceLocation;
    private StockLocation destinationLocation;
    private String description;
    private String createdBy;
    private Instant createdAt;
    private List<StockTransferLineResponse> lines;
}
