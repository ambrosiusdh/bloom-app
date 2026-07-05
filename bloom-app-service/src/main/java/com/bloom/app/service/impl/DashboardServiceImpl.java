package com.bloom.app.service.impl;

import com.bloom.app.api.dto.response.dashboard.CategoryDto;
import com.bloom.app.api.dto.response.dashboard.ChartDataPoint;
import com.bloom.app.api.dto.response.dashboard.DashboardResponse;
import com.bloom.app.api.dto.response.dashboard.LowStockDto;
import com.bloom.app.api.dto.response.dashboard.RevenueChartDto;
import com.bloom.app.api.dto.response.dashboard.SummaryDto;
import com.bloom.app.api.dto.response.dashboard.TransactionDto;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.domain.properties.BloomProperties;
import com.bloom.app.persistence.projection.TopCategoryProjection;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;
    private final BloomProperties bloomProperties;

    @Override
    public DashboardResponse getDashboardOverview() {
        log.debug("Getting dashboard overview");

        return DashboardResponse.builder()
            .summary(getSummaryCards())
            .revenueChart(getRevenueChart())
            .recentTransactions(getRecentTransactions())
            .topCategories(getTopCategories())
            .lowStock(getLowStockItems())
            .build();
    }

    private List<SummaryDto> getSummaryCards() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();

        Instant startOfDay = today.atStartOfDay(zoneId).toInstant();
        Instant endOfDay = today.atTime(LocalTime.MAX).atZone(zoneId).toInstant();

        List<Sale> todaySales = saleRepository.findByCreatedAtBetween(startOfDay, endOfDay);

        BigDecimal todayRevenue = todaySales.stream()
            .map(Sale::getTotalAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        long todayOrders = todaySales.size();
        long totalItemsSold = todaySales.stream()
            .mapToLong(sale -> sale.getItems().stream().mapToInt(SaleItem::getQuantity).sum())
            .sum();

        return List.of(
            createSummaryCard("Total Pendapatan", todayRevenue, true),
            createSummaryCard("Total Pesanan", BigDecimal.valueOf(totalItemsSold), false),
            createSummaryCard("Total Transaksi", BigDecimal.valueOf(todayOrders), false));
    }

    private SummaryDto createSummaryCard(String label, BigDecimal current, boolean isCurrency) {
        String value = isCurrency ? String.format("Rp. %,.0f", current) : String.valueOf(current.intValue());

        return SummaryDto.builder()
            .label(label)
            .summary(value)
            .trend(null)
            .isPositive(null)
            .build();
    }

    private RevenueChartDto getRevenueChart() {
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        Locale idLocale = Locale.forLanguageTag("id-ID");

        // Last 7 days
        List<ChartDataPoint> weekData = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            Instant start = date.atStartOfDay(zoneId).toInstant();
            Instant end = date.atTime(LocalTime.MAX).atZone(zoneId).toInstant();

            List<Sale> sales = saleRepository.findByCreatedAtBetween(start, end);
            BigDecimal revenue = sales.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            weekData.add(ChartDataPoint.builder()
                .name(date.getDayOfWeek().getDisplayName(TextStyle.FULL, idLocale))
                .revenue(revenue)
                .build());
        }

        // Current month weekly
        List<ChartDataPoint> monthData = new ArrayList<>();
        YearMonth currentMonth = YearMonth.now();
        LocalDate firstDay = currentMonth.atDay(1);
        LocalDate lastDay = currentMonth.atEndOfMonth();

        // Handle year transition for weeks if necessary, but simple iteration is safer
        LocalDate current = firstDay;
        int weekNum = 1;
        while (!current.isAfter(lastDay)) {
            LocalDate endOfWeek = current.plusDays(6);
            if (endOfWeek.isAfter(lastDay))
                endOfWeek = lastDay;

            Instant start = current.atStartOfDay(zoneId).toInstant();
            Instant end = endOfWeek.atTime(LocalTime.MAX).atZone(zoneId).toInstant();

            List<Sale> sales = saleRepository.findByCreatedAtBetween(start, end);
            BigDecimal revenue = sales.stream()
                .map(Sale::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            monthData.add(ChartDataPoint.builder()
                .name("Minggu " + weekNum)
                .revenue(revenue)
                .build());

            current = current.plusDays(7);
            weekNum++;
        }

        return RevenueChartDto.builder()
            .week(weekData)
            .month(monthData)
            .build();
    }

    private List<TransactionDto> getRecentTransactions() {
        return saleRepository.findAll(PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt")))
            .getContent().stream()
            .map(sale -> TransactionDto.builder()
                .id(sale.getCode())
                .time(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(sale.getCreatedAt()))
                .admin(sale.getCreatedBy() != null ? sale.getCreatedBy() : "Sistem")
                .build())
            .collect(Collectors.toList());
    }

    private List<CategoryDto> getTopCategories() {
        List<TopCategoryProjection> topCategoriesProjections = saleRepository.findTopCategories(PageRequest.of(0, 4));
        List<CategoryDto> topCategories = topCategoriesProjections.stream()
            .map(proj -> CategoryDto.builder()
                .name(proj.getName())
                .value(proj.getTotal())
                .build())
            .collect(Collectors.toList());
        BigDecimal totalRevenue = Optional.ofNullable(saleRepository.getTotalRevenue()).orElse(BigDecimal.ZERO);

        BigDecimal topCategoriesRevenue = topCategories.stream()
            .map(CategoryDto::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal othersRevenue = totalRevenue.subtract(topCategoriesRevenue);

        if (othersRevenue.compareTo(BigDecimal.ZERO) > 0) {
            // Since list from repository might be immutable or fixed size, create a new
            // list
            List<CategoryDto> result = new ArrayList<>(topCategories);
            result.add(CategoryDto.builder()
                .name("Lainnya")
                .value(othersRevenue)
                .build());
            return result;
        }

        return topCategories;
    }

    private List<LowStockDto> getLowStockItems() {
        return itemRepository.findByStockQuantityLessThan(bloomProperties.getLowStockThreshold()).stream()
            .limit(5)
            .map(item -> LowStockDto.builder()
                .id(item.getId())
                .name(item.getName())
                .sku(item.getSku())
                .stock(item.getTotalStock())
                .minStock(bloomProperties.getLowStockThreshold())
                .category(item.getCategory().getName())
                .build())
            .collect(Collectors.toList());
    }
}
