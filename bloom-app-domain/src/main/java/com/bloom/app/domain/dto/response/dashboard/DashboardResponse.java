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
public class DashboardResponse {
    private List<SummaryDto> summary;
    private RevenueChartDto revenueChart;
    private List<TransactionDto> recentTransactions;
    private List<CategoryDto> topCategories;
    private List<LowStockDto> lowStock;
}
