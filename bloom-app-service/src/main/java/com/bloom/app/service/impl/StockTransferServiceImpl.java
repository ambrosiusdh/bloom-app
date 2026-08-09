package com.bloom.app.service.impl;

import com.bloom.app.api.dto.request.stocktransfer.CreateStockTransferRequest;
import com.bloom.app.api.dto.request.stocktransfer.StockTransferLineRequest;
import com.bloom.app.api.dto.response.stocktransfer.StockTransferResponse;
import com.bloom.app.domain.enums.DocumentType;
import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.IdempotencyConflictException;
import com.bloom.app.domain.exception.InsufficientStockException;
import com.bloom.app.domain.exception.ResourceNotFoundException;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockTransfer;
import com.bloom.app.domain.model.StockTransferLine;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.StockTransferRepository;
import com.bloom.app.service.DocumentCounterService;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.service.StockTransferService;
import com.bloom.app.service.mapper.StockTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockTransferServiceImpl implements StockTransferService {
    static final int MAX_REQUEST_KEY_LENGTH = 100;

    private final StockTransferRepository stockTransferRepository;
    private final ItemRepository itemRepository;
    private final StockTransferMapper stockTransferMapper;
    private final StockMovementService stockMovementService;
    private final DocumentCounterService documentCounterService;

    @Override
    @Transactional
    public StockTransferResponse createStockTransfer(
            String requestKey, CreateStockTransferRequest request) {
        String normalizedRequestKey = validateAndNormalizeRequestKey(requestKey);
        validateRequestShape(request);
        String requestHash = requestHash(request);

        // Serialize only callers using the same key, so a concurrent retry observes
        // the first committed result instead of racing the unique constraint.
        stockTransferRepository.lockRequestKey(normalizedRequestKey);
        StockTransfer existing = stockTransferRepository.findByRequestKey(normalizedRequestKey)
            .orElse(null);
        if (existing != null) {
            if (!existing.getRequestHash().equals(requestHash)) {
                throw new IdempotencyConflictException();
            }
            return stockTransferMapper.toResponse(existing);
        }

        List<String> requestedSkus = request.getLines().stream()
            .map(line -> line.getItemSku().trim())
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

        List<PreparedLine> preparedLines = new ArrayList<>();
        for (StockTransferLineRequest lineRequest : request.getLines()) {
            Item item = itemBySku.get(lineRequest.getItemSku().trim());
            validateLineAgainstItem(lineRequest, item, request.getSourceLocation());
            preparedLines.add(new PreparedLine(item, lineRequest.getQuantity()));
        }
        // Item rows have already been locked by ascending id. Persist movements in
        // that same order so every multi-item stock transaction is deterministic.
        preparedLines.sort(Comparator.comparing(line -> line.item().getId()));

        StockTransfer transfer = stockTransferMapper.createRequestToEntity(request);
        transfer.setDescription(normalizeDescription(request.getDescription()));
        transfer.setRequestKey(normalizedRequestKey);
        transfer.setRequestHash(requestHash);
        transfer.setCode(documentCounterService.generateNextCode(DocumentType.STOCK_TRANSFER));

        List<StockTransferLine> transferLines = new ArrayList<>(preparedLines.stream()
            .map(line -> StockTransferLine.builder()
                .stockTransfer(transfer)
                .item(line.item())
                .itemSku(line.item().getSku())
                .unitOfMeasure(line.item().getBaseUnitOfMeasure())
                .quantity(normalizeStoredQuantity(line.quantity()))
                .build())
            .toList());
        transfer.setLines(transferLines);
        StockTransfer savedTransfer = stockTransferRepository.saveAndFlush(transfer);

        for (StockTransferLine line : savedTransfer.getLines()) {
            stockMovementService.recordMovement(
                MovementSourceType.TRANSFER,
                savedTransfer.getId(),
                line.getItem(),
                line.getQuantity(),
                MovementType.OUT,
                savedTransfer.getCode(),
                savedTransfer.getSourceLocation()
            );
            stockMovementService.recordMovement(
                MovementSourceType.TRANSFER,
                savedTransfer.getId(),
                line.getItem(),
                line.getQuantity(),
                MovementType.IN,
                savedTransfer.getCode(),
                savedTransfer.getDestinationLocation()
            );
        }

        savedTransfer.setCreatedAt(
            stockTransferRepository.findPersistedCreatedAtById(savedTransfer.getId()));
        log.debug("Created stock transfer {} with {} lines", savedTransfer.getCode(), transferLines.size());
        return stockTransferMapper.toResponse(savedTransfer);
    }

    @Override
    @Transactional(readOnly = true)
    public StockTransferResponse getStockTransferDetails(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Stock transfer code is required");
        }
        StockTransfer transfer = stockTransferRepository.findByCode(code)
            .orElseThrow(() -> new ResourceNotFoundException("Stock transfer not found: " + code));
        return stockTransferMapper.toResponse(transfer);
    }

    private void validateRequestShape(CreateStockTransferRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Stock transfer request is required");
        }
        if (request.getSourceLocation() == null) {
            throw new IllegalArgumentException("Source location is required");
        }
        if (request.getDestinationLocation() == null) {
            throw new IllegalArgumentException("Destination location is required");
        }
        if (request.getSourceLocation() == request.getDestinationLocation()) {
            throw new IllegalArgumentException("Source and destination locations must differ");
        }
        if (request.getDescription() != null && request.getDescription().length() > 255) {
            throw new IllegalArgumentException("Description must not exceed 255 characters");
        }
        if (request.getLines() == null || request.getLines().isEmpty()) {
            throw new IllegalArgumentException("Transfer lines are required");
        }

        Set<String> skus = new HashSet<>();
        for (StockTransferLineRequest line : request.getLines()) {
            if (line == null) {
                throw new IllegalArgumentException("Transfer line is required");
            }
            if (line.getItemSku() == null || line.getItemSku().isBlank()) {
                throw new IllegalArgumentException("Item SKU is required");
            }
            String sku = line.getItemSku().trim();
            if (!skus.add(sku)) {
                throw new IllegalArgumentException("Duplicate item lines are not allowed: " + sku);
            }
            if (line.getUnitOfMeasure() == null) {
                throw new IllegalArgumentException("Unit of measure is required for item: " + sku);
            }
            InventoryQuantityValidator.validateIncoming(line.getQuantity(), true);
        }
    }

    private void validateLineAgainstItem(
            StockTransferLineRequest lineRequest, Item item, StockLocation sourceLocation) {
        if (lineRequest.getUnitOfMeasure() != item.getBaseUnitOfMeasure()) {
            throw new IllegalArgumentException(
                "Unit of measure must match base unit " + item.getBaseUnitOfMeasure()
                    + " for item: " + item.getSku()
            );
        }
        InventoryQuantityValidator.validateIncoming(
            lineRequest.getQuantity(), item.isFractionalQuantityAllowed());
        BigDecimal available = stockAt(item, sourceLocation);
        if (available.compareTo(lineRequest.getQuantity()) < 0) {
            throw new InsufficientStockException(item.getSku(), sourceLocation);
        }
    }

    private BigDecimal stockAt(Item item, StockLocation location) {
        BigDecimal stock = switch (location) {
            case STORE -> item.getStockStore();
            case WAREHOUSE -> item.getStockWarehouse();
        };
        return stock == null ? BigDecimal.ZERO : stock;
    }

    private String validateAndNormalizeRequestKey(String requestKey) {
        if (requestKey == null || requestKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key header is required");
        }
        String normalized = requestKey.trim();
        if (normalized.length() > MAX_REQUEST_KEY_LENGTH) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 100 characters");
        }
        return normalized;
    }

    private String requestHash(CreateStockTransferRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateHashField(digest, request.getSourceLocation().name());
            updateHashField(digest, request.getDestinationLocation().name());
            updateHashField(digest, canonicalDescription(request.getDescription()));
            List<StockTransferLineRequest> sortedLines = request.getLines().stream()
                .sorted(Comparator.comparing(line -> line.getItemSku().trim()))
                .toList();
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(sortedLines.size()).array());
            for (StockTransferLineRequest line : sortedLines) {
                updateHashField(digest, line.getItemSku().trim());
                updateHashField(digest, canonicalQuantity(line.getQuantity()));
                updateHashField(digest, line.getUnitOfMeasure().name());
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

    private String canonicalQuantity(BigDecimal quantity) {
        return quantity.stripTrailingZeros().toPlainString();
    }

    private BigDecimal normalizeStoredQuantity(BigDecimal quantity) {
        return quantity.setScale(InventoryQuantityValidator.MAX_SCALE);
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        return description.trim();
    }

    private String canonicalDescription(String description) {
        String normalized = normalizeDescription(description);
        return normalized == null ? "" : normalized;
    }

    private record PreparedLine(Item item, BigDecimal quantity) {
    }
}
