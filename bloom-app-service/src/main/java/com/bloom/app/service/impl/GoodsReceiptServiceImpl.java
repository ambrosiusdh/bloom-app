package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.goodsreceipt.CancelGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptItemRequest;
import com.bloom.app.api.dto.request.goodsreceipt.CreateGoodsReceiptRequest;
import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.api.dto.request.supplierpayment.CreateSupplierPaymentRequest;
import com.bloom.app.api.dto.response.goodsreceipt.GoodsReceiptResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.GoodsReceiptStatus;
import com.bloom.app.domain.error.ErrorCode;
import com.bloom.app.domain.exception.BusinessException;
import com.bloom.app.domain.exception.GoodsReceiptConflictException;
import com.bloom.app.domain.exception.GoodsReceiptIdempotencyConflictException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.GoodsReceipt;
import com.bloom.app.domain.model.GoodsReceiptItem;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Supplier;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import com.bloom.app.persistence.repository.GoodsReceiptRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.SupplierRepository;
import com.bloom.app.service.DocumentCounterService;
import com.bloom.app.service.GoodsReceiptService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.SupplierPaymentService;
import com.bloom.app.service.mapper.GoodsReceiptMapper;
import com.bloom.app.service.mapper.SupplierMapper;
import com.bloom.app.service.specification.GoodsReceiptSpecification;
import com.bloom.app.service.util.CashMoneyUtil;
import com.bloom.app.service.util.CurrentActorProvider;
import com.bloom.app.service.util.SupplierDebtCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsReceiptServiceImpl implements GoodsReceiptService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;
    private static final int MAX_RECEIPT_CODE_LENGTH = 100;

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final ItemRepository itemRepository;
    private final StockMovementService stockMovementService;
    private final DocumentCounterService documentCounterService;
    private final GoodsReceiptMapper goodsReceiptMapper;
    private final SupplierRepository supplierRepository;
    private final CurrentActorProvider currentActorProvider;
    private final SupplierPaymentService supplierPaymentService;
    private final SupplierDebtCalculator supplierDebtCalculator;

    @Override
    @Transactional
    public GoodsReceiptResponse createGoodsReceipt(
            String idempotencyKey, CreateGoodsReceiptRequest request) {
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        validateRequestShape(request);
        String requestHash = createRequestHash(request);

        goodsReceiptRepository.lockCreateIdempotencyKey(normalizedKey);
        GoodsReceipt existing = goodsReceiptRepository
            .findByCreateIdempotencyKey(normalizedKey)
            .orElse(null);
        if (existing != null) {
            if (!existing.getCreateRequestHash().equals(requestHash)) {
                throw new GoodsReceiptIdempotencyConflictException();
            }
            return mapResponse(existing);
        }

        String supplierCode = SupplierMapper.normalizeCode(request.getSupplierCode());
        Supplier supplier = supplierRepository.findByCodeForUpdate(supplierCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.SUPPLIER_NOT_FOUND, supplierCode));
        if (!supplier.isActive()) {
            throw new IllegalArgumentException("Supplier must be active: " + supplierCode);
        }

        List<String> requestedSkus = request.getItems().stream()
            .map(line -> line.getItemSku().trim())
            .distinct()
            .sorted()
            .toList();
        List<Item> lockedItems = itemRepository.findBySkuInOrderByIdForUpdate(requestedSkus);
        Map<String, Item> itemBySku = new HashMap<>();
        lockedItems.forEach(item -> itemBySku.put(item.getSku(), item));
        if (lockedItems.size() != requestedSkus.size()) {
            String missingSku = requestedSkus.stream()
                .filter(sku -> !itemBySku.containsKey(sku))
                .findFirst()
                .orElse("unknown");
            throw new ResourceNotFoundException("Item not found: " + missingSku);
        }

        GoodsReceipt receipt = GoodsReceipt.builder()
            .code(documentCounterService.generateNextCode(DocumentType.GOODS_RECEIPT))
            .receivedDate(request.getReceivedDate())
            .supplier(supplier)
            .supplierNameSnapshot(supplier.getName())
            .createIdempotencyKey(normalizedKey)
            .createRequestHash(requestHash)
            .status(GoodsReceiptStatus.POSTED)
            .description(normalizeOptional(request.getDescription()))
            .build();

        List<GoodsReceiptItem> lines = new ArrayList<>();
        BigDecimal receiptTotal = BigDecimal.ZERO;
        for (CreateGoodsReceiptItemRequest lineRequest : request.getItems()) {
            Item item = itemBySku.get(lineRequest.getItemSku().trim());
            InventoryQuantityValidator.validateIncoming(
                lineRequest.getQuantity(), item.isFractionalQuantityAllowed());
            BigDecimal quantity = lineRequest.getQuantity()
                .setScale(InventoryQuantityValidator.MAX_SCALE, RoundingMode.UNNECESSARY);
            BigDecimal purchasePrice = CashMoneyUtil.requirePositive(
                lineRequest.getPurchasePrice(), "Purchase price");
            BigDecimal lineTotal = quantity.multiply(purchasePrice)
                .setScale(CashMoneyUtil.SCALE, RoundingMode.HALF_UP);
            CashMoneyUtil.requirePositive(lineTotal, "Goods receipt line total");

            lines.add(GoodsReceiptItem.builder()
                .goodsReceipt(receipt)
                .item(item)
                .quantity(quantity)
                .purchasePrice(purchasePrice)
                .lineTotal(lineTotal)
                .baseUnitOfMeasure(item.getBaseUnitOfMeasure())
                .stockLocation(lineRequest.getStockLocation())
                .build());
            receiptTotal = receiptTotal.add(lineTotal);
        }

        receipt.setItems(lines);
        receipt.setTotalAmount(CashMoneyUtil.requirePositive(
            receiptTotal.setScale(CashMoneyUtil.SCALE, RoundingMode.HALF_UP),
            "Goods receipt total"));
        GoodsReceipt savedReceipt = goodsReceiptRepository.saveAndFlush(receipt);
        stockMovementService.recordGoodsReceiptPosting(savedReceipt);
        if (request.getInitialPayment() != null) {
            supplierPaymentService.createInitialPayment(
                savedReceipt,
                initialPaymentIdempotencyKey(normalizedKey),
                request.getInitialPayment()
            );
        }

        log.debug("Created and posted goods receipt {}", savedReceipt.getCode());
        return mapResponse(savedReceipt);
    }

    @Override
    @Transactional
    public GoodsReceiptResponse cancelGoodsReceipt(
            String code, CancelGoodsReceiptRequest request) {
        String normalizedCode = requireCode(code);
        if (request == null) {
            throw new IllegalArgumentException("Cancel goods receipt request is required");
        }
        String reason = normalizeRequired(request.getReason(), "Cancellation reason");
        GoodsReceipt receipt = goodsReceiptRepository.findByCodeForUpdate(normalizedCode)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.GOODS_RECEIPT_NOT_FOUND.formatMessage(normalizedCode)));

        if (receipt.getStatus() == GoodsReceiptStatus.CANCELLED) {
            return mapResponse(receipt);
        }
        if (receipt.getStatus() != GoodsReceiptStatus.POSTED) {
            throw new GoodsReceiptConflictException(
                "Only a posted goods receipt can be cancelled: " + normalizedCode);
        }
        if (supplierDebtCalculator.validPaidAmount(receipt.getId()).signum() > 0) {
            throw new GoodsReceiptConflictException(
                "Goods receipt cannot be cancelled while it has active payments: "
                    + normalizedCode);
        }

        List<Long> itemIds = receipt.getItems().stream()
            .map(line -> line.getItem().getId())
            .distinct()
            .sorted()
            .toList();
        Map<Long, Item> lockedItems = itemRepository.findByIdInOrderByIdForUpdate(itemIds).stream()
            .collect(Collectors.toMap(Item::getId, item -> item));
        if (lockedItems.size() != itemIds.size()) {
            throw new GoodsReceiptConflictException(
                "A receipt item no longer exists; cancellation cannot safely reverse stock");
        }
        receipt.getItems().forEach(line -> line.setItem(lockedItems.get(line.getItem().getId())));

        stockMovementService.recordGoodsReceiptCancellation(receipt);
        receipt.setStatus(GoodsReceiptStatus.CANCELLED);
        receipt.setCancellationReason(reason);
        receipt.setCancelledAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        receipt.setCancelledBy(currentActorProvider.username());
        GoodsReceipt savedReceipt = goodsReceiptRepository.saveAndFlush(receipt);
        return mapResponse(savedReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsReceiptResponse getGoodsReceiptDetails(String code) {
        String normalizedCode = requireCode(code);
        GoodsReceipt goodsReceipt = goodsReceiptRepository.findDetailsByCode(normalizedCode)
            .orElseThrow(() -> new ResourceNotFoundException(
                ErrorCode.GOODS_RECEIPT_NOT_FOUND.formatMessage(normalizedCode)));
        return mapResponse(goodsReceipt);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GoodsReceiptResponse> filterGoodsReceipts(
            FilterGoodsReceiptRequest request, Pageable pageable) {
        FilterGoodsReceiptRequest effectiveRequest = request == null
            ? new FilterGoodsReceiptRequest() : request;
        Specification<GoodsReceipt> spec = GoodsReceiptSpecification.filter(effectiveRequest);
        Page<GoodsReceipt> page = goodsReceiptRepository.findAll(spec, pageable);
        Map<Long, BigDecimal> paidByReceipt = supplierDebtCalculator.validPaidAmounts(
            page.getContent().stream().map(GoodsReceipt::getId).toList());
        List<GoodsReceiptResponse> responses = page.getContent().stream()
            .map(receipt -> mapResponse(
                receipt, paidByReceipt.getOrDefault(receipt.getId(), BigDecimal.ZERO)))
            .collect(Collectors.toList());
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    private void validateRequestShape(CreateGoodsReceiptRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Create goods receipt request is required");
        }
        if (request.getReceivedDate() == null) {
            throw new IllegalArgumentException("Received date is required");
        }
        if (request.getSupplierCode() == null || request.getSupplierCode().isBlank()) {
            throw new IllegalArgumentException("Supplier code is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Goods receipt items are required");
        }
        for (CreateGoodsReceiptItemRequest line : request.getItems()) {
            if (line == null) {
                throw new IllegalArgumentException("Goods receipt line is required");
            }
            if (line.getItemSku() == null || line.getItemSku().isBlank()) {
                throw new IllegalArgumentException("Item SKU is required");
            }
            if (line.getStockLocation() == null) {
                throw new IllegalArgumentException("Stock location is required");
            }
            InventoryQuantityValidator.validateIncoming(line.getQuantity(), true);
            CashMoneyUtil.requirePositive(line.getPurchasePrice(), "Purchase price");
        }
        validateInitialPayment(request.getInitialPayment());
    }

    private String normalizeIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String normalized = key.trim();
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IllegalArgumentException(
                "Idempotency-Key must not exceed 100 characters");
        }
        return normalized;
    }

    private String createRequestHash(CreateGoodsReceiptRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHashField(digest, SupplierMapper.normalizeCode(request.getSupplierCode()));
            updateHashField(digest, request.getReceivedDate().toString());
            updateHashField(digest, canonicalOptional(request.getDescription()));
            updateInitialPaymentHash(digest, request.getInitialPayment());
            List<CreateGoodsReceiptItemRequest> sortedLines = request.getItems().stream()
                .sorted(Comparator
                    .comparing((CreateGoodsReceiptItemRequest line) -> line.getItemSku().trim())
                    .thenComparing(line -> line.getStockLocation().name())
                    .thenComparing(line -> canonicalDecimal(line.getQuantity()))
                    .thenComparing(line -> canonicalDecimal(line.getPurchasePrice())))
                .toList();
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(sortedLines.size()).array());
            for (CreateGoodsReceiptItemRequest line : sortedLines) {
                updateHashField(digest, line.getItemSku().trim());
                updateHashField(digest, line.getStockLocation().name());
                updateHashField(digest, canonicalDecimal(line.getQuantity()));
                updateHashField(digest, canonicalDecimal(line.getPurchasePrice()));
            }
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

    private String canonicalOptional(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? "" : normalized;
    }

    private String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void validateInitialPayment(CreateSupplierPaymentRequest payment) {
        if (payment == null) {
            return;
        }
        CashMoneyUtil.requirePositive(payment.getAmount(), "Supplier payment amount");
        if (payment.getPaymentMethod() == null) {
            throw new IllegalArgumentException("Payment method is required");
        }
        if (payment.getPaidAt() == null) {
            throw new IllegalArgumentException("Paid at is required");
        }
    }

    private void updateInitialPaymentHash(
            MessageDigest digest, CreateSupplierPaymentRequest payment) {
        updateHashField(digest, payment == null ? "NO_INITIAL_PAYMENT" : "INITIAL_PAYMENT");
        if (payment == null) {
            return;
        }
        updateHashField(digest, canonicalDecimal(payment.getAmount()));
        updateHashField(digest, payment.getPaymentMethod().name());
        updateHashField(digest, payment.getPaidAt().toString());
        updateHashField(digest, canonicalOptional(payment.getReference()));
        updateHashField(digest, canonicalOptional(payment.getNote()));
    }

    private String initialPaymentIdempotencyKey(String receiptIdempotencyKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(receiptIdempotencyKey.getBytes(StandardCharsets.UTF_8));
            return "RECEIPT-INITIAL:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private GoodsReceiptResponse mapResponse(GoodsReceipt receipt) {
        return mapResponse(receipt, supplierDebtCalculator.validPaidAmount(receipt.getId()));
    }

    private GoodsReceiptResponse mapResponse(GoodsReceipt receipt, BigDecimal paidAmount) {
        return supplierDebtCalculator.apply(
            goodsReceiptMapper.toResponse(receipt), receipt, paidAmount);
    }

    private String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value.trim();
    }

    private String requireCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Goods receipt code is required");
        }
        String normalizedCode = code.trim();
        if (normalizedCode.length() > MAX_RECEIPT_CODE_LENGTH) {
            throw new IllegalArgumentException(
                "Goods receipt code must not exceed " + MAX_RECEIPT_CODE_LENGTH + " characters");
        }
        return normalizedCode;
    }
}
