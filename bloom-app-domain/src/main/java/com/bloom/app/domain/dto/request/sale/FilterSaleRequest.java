package com.bloom.app.domain.dto.request.sale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FilterSaleRequest {
    private String code;
    private Instant startDate;
    private Instant endDate;
}
