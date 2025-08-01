package com.bloom.app.service.impl;

import com.bloom.app.domain.dto.request.sale.CreateSaleRequest;
import com.bloom.app.domain.dto.request.sale.FilterSaleRequest;
import com.bloom.app.domain.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.domain.dto.response.item.ItemResponse;
import com.bloom.app.domain.dto.response.sale.SaleResponse;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.repository.ItemRepository;
import com.bloom.app.repository.SaleRepository;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.mapper.SaleMapper;
import com.bloom.app.service.specification.ItemSpecification;
import com.bloom.app.service.specification.SaleSpecification;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {
    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;
    private final SaleMapper saleMapper;

    @Override
    @Transactional
    public SaleResponse createSale(CreateSaleRequest request) {
        log.debug("SaleService createSale with request: {}", request);
        Sale sale = saleMapper.createRequestToEntity(request);
        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        if (request.getSaleItemList() == null || request.getSaleItemList().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart is empty");
        }

        for (CreateSaleItemRequest itemRequest : request.getSaleItemList()) {
            Item item = itemRepository.findItemBySku(itemRequest.getItemSku())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + itemRequest.getItemSku()));

            if (item.getStockQuantity() < itemRequest.getQuantity()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cart quantity is more than stock quantity");
            }

            BigDecimal unitPrice = BigDecimal.valueOf(item.getPrice());
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            SaleItem saleItem = SaleItem.builder()
                .item(item)
                .quantity(itemRequest.getQuantity())
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .sale(sale)
                .build();

            saleItems.add(saleItem);
            total = total.add(subtotal);
            item.setStockQuantity(item.getStockQuantity() - itemRequest.getQuantity());
        }

        BigDecimal discount = Optional.ofNullable(request.getDiscountAmount()).orElse(BigDecimal.ZERO);
        sale.setCode(generateMonthlySaleCode());
        sale.setItems(saleItems);
        sale.setTotalAmount(discount);

        return saleMapper.saleToResponse(saleRepository.save(sale));
    }

    @Override
    public Page<SaleResponse> filterSales(FilterSaleRequest request, Pageable pageable) {
        log.debug("SaleService filterSale with request: {}", request);

        Page<Sale> salePage = saleRepository.findAll(SaleSpecification.filter(request), pageable);

        List<SaleResponse> saleResponseList = salePage.getContent()
            .stream()
            .map(saleMapper::saleToResponse)
            .toList();

        return new PageImpl<>(saleResponseList, pageable, salePage.getTotalElements());
    }

    @Override
    public SaleResponse getSaleDetails(String code) {
        log.debug("SaleService getSaleDetails with code: {}", code);
        Sale sale = saleRepository.findByCode(code)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sales not found"));

        return saleMapper.saleToResponse(sale);
    }

    public String generateMonthlySaleCode() {
        YearMonth currentMonth = YearMonth.now();
        int month = currentMonth.getMonthValue();
        int year = currentMonth.getYear();
        ZoneId zoneId = ZoneId.systemDefault();

        Instant startOfMonth = currentMonth.atDay(1).atStartOfDay(zoneId).toInstant();
        Instant endOfMonth = currentMonth.atEndOfMonth().atTime(23, 59, 59).atZone(zoneId).toInstant();

        long count = saleRepository.countByCreatedAtBetween(startOfMonth, endOfMonth);
        long nextSequence = count + 1;

        return String.format("SALE/%02d-%d/%04d", month, year, nextSequence);
    }
}
