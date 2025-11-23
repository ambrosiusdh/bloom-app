package com.bloom.app.domain.dto.response.dashboard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueChartDto {
    private List<ChartDataPoint> week;
    private List<ChartDataPoint> month;
}
