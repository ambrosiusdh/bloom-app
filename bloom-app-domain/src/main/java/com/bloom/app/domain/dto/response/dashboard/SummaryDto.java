package com.bloom.app.domain.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryDto {
    private String summary;
    private String label;
    private String trend;
    private Boolean isPositive;
}
