package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.CashMovementType;
import com.bloom.app.domain.enums.CashSessionStatus;
import com.bloom.app.domain.enums.PaymentType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.api.dto.request.sale.CreateSaleRequest;
import com.bloom.app.api.dto.request.sale.FilterSaleRequest;
import com.bloom.app.api.dto.request.saleitem.CreateSaleItemRequest;
import com.bloom.app.api.dto.response.sale.SaleCheckoutStatusResponse;
import com.bloom.app.api.dto.response.sale.SaleResponse;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.CashSessionConflictException;
import com.bloom.app.domain.exception.CheckoutIdempotencyConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.CashSession;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.SaleItem;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.CashSessionRepository;
import com.bloom.app.persistence.repository.SaleRepository;
import com.bloom.app.service.ExcelExportService;
import com.bloom.app.service.CashMovementService;
import com.bloom.app.service.SaleService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.command.RecordCashMovementCommand;
import com.bloom.app.service.mapper.SaleMapper;
import com.bloom.app.service.specification.SaleSpecification;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import com.bloom.app.service.util.CashMoneyUtil;
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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SaleServiceImpl implements SaleService {
    static final int MAX_CHECKOUT_KEY_LENGTH = 100;

    private final SaleRepository saleRepository;
    private final ItemRepository itemRepository;
    private final CashSessionRepository cashSessionRepository;
    private final SaleMapper saleMapper;
    private final ExcelExportService excelExportService;
    private final StockMovementService stockMovementService;
    private final CashMovementService cashMovementService;
    private final DocumentCounterServiceImpl documentCounterService;

    @Override
    @Transactional
    public SaleResponse createSale(String checkoutIdempotencyKey, CreateSaleRequest request) {
        String normalizedCheckoutKey = validateAndNormalizeCheckoutKey(checkoutIdempotencyKey);
        validateRequestShape(request);
        String checkoutRequestHash = checkoutRequestHash(request);
        log.debug("SaleService createSale with idempotency key: {}", normalizedCheckoutKey);

        saleRepository.lockCheckoutKey(normalizedCheckoutKey);
        Sale existing = saleRepository.findByCheckoutIdempotencyKey(normalizedCheckoutKey)
            .orElse(null);
        if (existing != null) {
            if (!existing.getCheckoutRequestHash().equals(checkoutRequestHash)) {
                throw new CheckoutIdempotencyConflictException();
            }
            return saleMapper.saleToResponse(existing);
        }

        CashSession cashSession = cashSessionRepository
            .findFirstByStatusForUpdate(CashSessionStatus.OPEN)
            .orElseThrow(() -> new CashSessionConflictException(
                "Checkout requires an open cash session"));

        Sale sale = saleMapper.createRequestToEntity(request);
        List<SaleItem> saleItems = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

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
        BigDecimal discount = CashMoneyUtil.requireNonNegative(
            request.getDiscountAmount(), "Discount amount");
        BigDecimal totalAmount = subtotalAmount.subtract(discount).setScale(4, RoundingMode.HALF_UP);
        CashMoneyUtil.requirePositive(totalAmount, "Total amount");
        BigDecimal paidAmount = CashMoneyUtil.requireNonNegative(
            request.getPaidAmount(), "Paid amount");
        BigDecimal changeAmount = validatePaymentAndCalculateChange(
            request.getPaymentType(), paidAmount, totalAmount);

        sale.setCode(documentCounterService.generateNextCode(DocumentType.SALE));
        sale.setCashSession(cashSession);
        sale.setCheckoutIdempotencyKey(normalizedCheckoutKey);
        sale.setCheckoutRequestHash(checkoutRequestHash);
        sale.setItems(saleItems);
        sale.setPaymentType(request.getPaymentType());
        sale.setSubtotalAmount(subtotalAmount);
        sale.setDiscountAmount(discount);
        sale.setTotalAmount(totalAmount);
        sale.setPaidAmount(paidAmount);
        sale.setChangeAmount(changeAmount);

        Sale savedSale = saleRepository.saveAndFlush(sale);
        stockMovementService.recordSaleMovements(savedSale);
        if (savedSale.getPaymentType() == PaymentType.CASH) {
            cashMovementService.recordMovement(new RecordCashMovementCommand(
                cashSession.getId(),
                CashMovementType.SALE_PAYMENT,
                savedSale.getId(),
                savedSale.getCode(),
                savedSale.getTotalAmount()
            ));
        }

        return saleMapper.saleToResponse(savedSale);
    }

    @Override
    @Transactional
    public SaleCheckoutStatusResponse getCheckoutStatus(String checkoutIdempotencyKey) {
        String normalizedCheckoutKey = validateAndNormalizeCheckoutKey(checkoutIdempotencyKey);
        // Intentionally not read-only: recovery must use the primary database transaction
        // and the same transaction-scoped advisory lock as checkout creation.
        saleRepository.lockCheckoutKey(normalizedCheckoutKey);

        return saleRepository.findByCheckoutIdempotencyKey(normalizedCheckoutKey)
            .map(sale -> SaleCheckoutStatusResponse.builder()
                .status(SaleCheckoutStatusResponse.Status.COMPLETED)
                .sale(saleMapper.saleToResponse(sale))
                .build())
            .orElseGet(() -> SaleCheckoutStatusResponse.builder()
                .status(SaleCheckoutStatusResponse.Status.UNKNOWN)
                .sale(null)
                .build());
    }

    private void validateRequestShape(CreateSaleRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Checkout request is required");
        }
        if (request.getPaymentType() == null) {
            throw new IllegalArgumentException("Payment type is required");
        }
        CashMoneyUtil.requireNonNegative(request.getPaidAmount(), "Paid amount");
        CashMoneyUtil.requireNonNegative(request.getDiscountAmount(), "Discount amount");
        if (request.getSaleItemList() == null || request.getSaleItemList().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        for (CreateSaleItemRequest itemRequest : request.getSaleItemList()) {
            validateSaleLineIdentity(itemRequest);
            InventoryQuantityValidator.validateIncoming(itemRequest.getQuantity(), true);
        }
    }

    private BigDecimal validatePaymentAndCalculateChange(
            PaymentType paymentType, BigDecimal paidAmount, BigDecimal totalAmount) {
        return switch (paymentType) {
            case CASH -> {
                if (paidAmount.compareTo(totalAmount) < 0) {
                    throw new BusinessException(ErrorCode.SALE_PAID_LESS_THAN_TOTAL);
                }
                yield paidAmount.subtract(totalAmount).setScale(4, RoundingMode.HALF_UP);
            }
            case QRIS -> {
                if (paidAmount.compareTo(totalAmount) != 0) {
                    throw new BusinessException(ErrorCode.SALE_QRIS_PAYMENT_MISMATCH);
                }
                yield BigDecimal.ZERO.setScale(4);
            }
        };
    }

    private Map<SaleLineKey, AggregatedSaleLine> aggregateSaleLines(
            List<CreateSaleItemRequest> itemRequests) {
        Set<String> requestedSkus = new LinkedHashSet<>();
        Map<SaleLineKey, AggregatedSaleLine> aggregatedLines = new LinkedHashMap<>();

        for (CreateSaleItemRequest itemRequest : itemRequests) {
            validateSaleLineIdentity(itemRequest);
            requestedSkus.add(itemRequest.getItemSku().trim());
        }

        Map<String, Item> itemsBySku = new LinkedHashMap<>();
        itemRepository.findBySkuInOrderByIdForUpdate(List.copyOf(requestedSkus))
            .forEach(item -> itemsBySku.put(item.getSku(), item));
        for (String sku : requestedSkus) {
            if (!itemsBySku.containsKey(sku)) {
                throw new ResourceNotFoundException("Item not found: " + sku);
            }
        }

        for (CreateSaleItemRequest itemRequest : itemRequests) {
            String sku = itemRequest.getItemSku().trim();
            StockLocation stockLocation = itemRequest.getStockLocation();
            Item item = itemsBySku.get(sku);
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

    private String validateAndNormalizeCheckoutKey(String checkoutKey) {
        if (checkoutKey == null || checkoutKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String normalized = checkoutKey.trim();
        if (normalized.length() > MAX_CHECKOUT_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 100 characters");
        }
        return normalized;
    }

    private String checkoutRequestHash(CreateSaleRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHashField(digest, request.getPaymentType().name());
            updateHashField(digest, canonicalDecimal(request.getPaidAmount()));
            updateHashField(digest, canonicalDecimal(request.getDiscountAmount()));
            updateHashField(digest, canonicalDescription(request.getDescription()));

            Map<CanonicalSaleLineKey, BigDecimal> canonicalLines = new TreeMap<>(
                Comparator.comparing(CanonicalSaleLineKey::itemSku)
                    .thenComparing(key -> key.stockLocation().name()));
            for (CreateSaleItemRequest line : request.getSaleItemList()) {
                CanonicalSaleLineKey key = new CanonicalSaleLineKey(
                    line.getItemSku().trim(), line.getStockLocation());
                canonicalLines.merge(key, line.getQuantity(), BigDecimal::add);
            }
            digest.update(ByteBuffer.allocate(Integer.BYTES)
                .putInt(canonicalLines.size()).array());
            canonicalLines.forEach((key, quantity) -> {
                updateHashField(digest, key.itemSku());
                updateHashField(digest, key.stockLocation().name());
                updateHashField(digest, canonicalDecimal(quantity));
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void updateHashField(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private String canonicalDecimal(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String canonicalDescription(String description) {
        return description == null || description.isBlank() ? "" : description.trim();
    }

    private void validateSaleLineIdentity(CreateSaleItemRequest itemRequest) {
        if (itemRequest == null) {
            throw new IllegalArgumentException("Sale item is required");
        }
        if (itemRequest.getItemSku() == null || itemRequest.getItemSku().isBlank()) {
            throw new IllegalArgumentException("Item SKU is required");
        }
        if (itemRequest.getStockLocation() == null) {
            throw new IllegalArgumentException("Stock location is required");
        }
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

    private record CanonicalSaleLineKey(String itemSku, StockLocation stockLocation) {
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
