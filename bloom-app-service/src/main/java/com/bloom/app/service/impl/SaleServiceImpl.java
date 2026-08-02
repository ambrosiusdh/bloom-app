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
import java.util.List;
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

        for (CreateSaleItemRequest itemRequest : request.getSaleItemList()) {
            Item item = itemRepository.findItemBySku(itemRequest.getItemSku())
                    .orElseThrow(() -> new ResourceNotFoundException("Item not found: " + itemRequest.getItemSku()));
            InventoryQuantityValidator.validateIncoming(
                itemRequest.getQuantity(), item.isFractionalQuantityAllowed());
            BigDecimal stockQuantity = itemRequest.getStockLocation().equals(StockLocation.STORE)
                ? item.getStockStore() : item.getStockWarehouse();

            if (stockQuantity.compareTo(itemRequest.getQuantity()) < 0) {
                throw new BusinessException(ErrorCode.SALE_INSUFFICIENT_STOCK_STORE, item.getName(), itemRequest.getStockLocation());
            }

            BigDecimal unitPrice = item.getPrice();
            BigDecimal subtotal = unitPrice.multiply(itemRequest.getQuantity()).setScale(4, RoundingMode.HALF_UP);

            SaleItem saleItem = SaleItem.builder()
                    .item(item)
                    .quantity(itemRequest.getQuantity())
                    .unitPrice(unitPrice)
                    .subtotal(subtotal)
                    .sale(sale)
                    .stockLocation(itemRequest.getStockLocation())
                    .build();

            saleItems.add(saleItem);
            total = total.add(subtotal);
        }

        BigDecimal discount = Optional.ofNullable(request.getDiscountAmount()).orElse(BigDecimal.ZERO);
        BigDecimal totalAmount = total.subtract(discount);
        if (sale.getPaidAmount().compareTo(sale.getTotalAmount()) < 0) {
            throw new BusinessException(ErrorCode.SALE_PAID_LESS_THAN_TOTAL);
        }

        sale.setCode(documentCounterService.generateNextCode(DocumentType.SALE));
        sale.setItems(saleItems);
        sale.setPaymentType(request.getPaymentType());
        sale.setSubtotalAmount(total);
        sale.setDiscountAmount(discount);
        sale.setTotalAmount(totalAmount);
        sale.setPaidAmount(request.getPaidAmount());

        Sale savedSale = saleRepository.save(sale);
        stockMovementService.recordSaleMovements(savedSale);

        return saleMapper.saleToResponse(savedSale);
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
