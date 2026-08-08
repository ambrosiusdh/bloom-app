package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.sale.FilterSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.service.ExcelExportService;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.mapper.SaleMapper;
import com.bloom.app.service.specification.SaleSpecification;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {
    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;
    private final SaleMapper saleMapper;
    private final ExcelExportService excelExportService;
    private final StockMovementService stockMovementService;
    private final DocumentCounterServiceImpl documentCounterService;

    @Override
    @Transactional
    public SaleResponse createSale(CreateSaleRequest request) {
        log.debug("SaleService createSale with request: {}", request);
        Sale sale = saleMapper.createRequestToEntity(request);
        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        if (request.getSaleItemList() == null || request.getSaleItemList().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        for (AggregatedSaleLine line : aggregateSaleLines(request.getSaleItemList()).values()) {
            Item item = line.item();
            BigDecimal stockQuantity = stockAt(item, line.stockLocation());

            if (stockQuantity.compareTo(line.quantity()) < 0) {
                throw new BusinessException(
                    ErrorCode.SALE_INSUFFICIENT_STOCK_STORE, item.getName(), line.stockLocation());
            }

            BigDecimal unitPrice = item.getPrice();
            if (unitPrice == null) {
                throw new IllegalArgumentException("Item price is required: " + item.getSku());
            }
            BigDecimal subtotal = unitPrice.multiply(line.quantity())
                .setScale(4, RoundingMode.HALF_UP);

            SaleItem saleItem = SaleItem.builder()
                    .item(item)
                    .quantity(line.quantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .sale(sale)
                    .stockLocation(line.stockLocation())
                    .build();

            saleItems.add(saleItem);
            total = total.add(subtotal);
        }

        BigDecimal subtotalAmount = total.setScale(4, RoundingMode.HALF_UP);
        BigDecimal discount = Optional.ofNullable(request.getDiscountAmount()).orElse(BigDecimal.ZERO);
        BigDecimal totalAmount = subtotalAmount.subtract(discount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal paidAmount = request.getPaidAmount();
        if (paidAmount == null) {
            throw new IllegalArgumentException("Paid amount is required");
        }
        if (paidAmount.compareTo(totalAmount) < 0) {
            throw new BusinessException(ErrorCode.SALE_PAID_LESS_THAN_TOTAL);
        }

        sale.setCode(documentCounterService.generateNextCode(DocumentType.SALE));
        sale.setItems(saleItems);
        sale.setPaymentType(request.getPaymentType());
        sale.setSubtotalAmount(subtotalAmount);
        sale.setDiscountAmount(discount);
        sale.setTotalAmount(totalAmount);
        sale.setPaidAmount(paidAmount);

        Sale savedSale = saleRepository.save(sale);
        stockMovementService.recordSaleMovements(savedSale);

        return saleMapper.saleToResponse(savedSale);
    }

    private Map<SaleLineKey, AggregatedSaleLine> aggregateSaleLines(
            List<CreateSaleItemRequest> itemRequests) {
        Map<String, Item> itemsBySku = new LinkedHashMap<>();
        Map<SaleLineKey, AggregatedSaleLine> aggregatedLines = new LinkedHashMap<>();

        for (CreateSaleItemRequest itemRequest : itemRequests) {
            if (itemRequest == null) {
                throw new IllegalArgumentException("Sale item is required");
            }
            String sku = itemRequest.getItemSku();
            if (sku == null || sku.isBlank()) {
                throw new IllegalArgumentException("Item SKU is required");
            }
            StockLocation stockLocation = itemRequest.getStockLocation();
            if (stockLocation == null) {
                throw new IllegalArgumentException("Stock location is required");
            }

            Item item = itemsBySku.computeIfAbsent(sku, key -> itemRepository.findItemBySku(key)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + key)));
            InventoryQuantityValidator.validateIncoming(
                itemRequest.getQuantity(), item.isFractionalQuantityAllowed());

            SaleLineKey key = new SaleLineKey(sku, stockLocation);
            aggregatedLines.merge(
                key,
                new AggregatedSaleLine(item, stockLocation, itemRequest.getQuantity()),
                (existing, incoming) -> new AggregatedSaleLine(
                    existing.item(),
                    existing.stockLocation(),
                    existing.quantity().add(incoming.quantity()))
            );
        }
        return aggregatedLines;
    }

    private BigDecimal stockAt(Item item, StockLocation stockLocation) {
        BigDecimal stock = switch (stockLocation) {
            case STORE -> item.getStockStore();
            case WAREHOUSE -> item.getStockWarehouse();
        };
        return stock == null ? BigDecimal.ZERO : stock;
    }

    private record SaleLineKey(String itemSku, StockLocation stockLocation) {
    }

    private record AggregatedSaleLine(
            Item item, StockLocation stockLocation, BigDecimal quantity) {
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

    @Override
    public void exportSale(String code, java.io.OutputStream outputStream) {
        log.debug("SaleService exportSale with code: {}", code);
        Sale sale = saleRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sale not found"));

        SaleResponse saleResponse = saleMapper.saleToResponse(sale);
        excelExportService.exportSaleToExcel(saleResponse, outputStream);
    }

    @Override
    public void exportSalesBulk(FilterSaleRequest request, java.io.OutputStream outputStream) {
        log.debug("SaleService exportSalesBulk with request: {}", request);
        List<Sale> sales = saleRepository.findAll(SaleSpecification.filter(request));

        try (ZipOutputStream zos = new ZipOutputStream(outputStream)) {
            for (Sale sale : sales) {
                SaleResponse saleResponse = saleMapper.saleToResponse(sale);
                String filename = String.format("Sale-%s.xlsx", sale.getCode().replaceAll("[/\\\\]", "-"));
                ZipEntry entry = new ZipEntry(filename);
                zos.putNextEntry(entry);
                excelExportService.exportSaleToExcel(saleResponse, zos);
                zos.closeEntry();
            }
            zos.finish();
        } catch (IOException e) {
            log.error("Failed to export sales bulk", e);
            throw new RuntimeException("Failed to export sales bulk", e);
        }
    }
}
