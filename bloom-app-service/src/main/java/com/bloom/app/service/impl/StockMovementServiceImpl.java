package com.bloom.app.service.impl;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.exception.BaseUnitOfMeasureImmutableException;
import com.bloom.app.domain.exception.InsufficientStockException;
import com.bloom.app.domain.exception.StockConcurrencyException;
import com.bloom.app.domain.model.*;
import com.bloom.app.persistence.repository.StockMovementRepository;
import com.bloom.app.persistence.repository.ItemRepository;
import com.bloom.app.persistence.repository.ItemAuditLogRepository;
import com.bloom.app.domain.enums.StockAdjustmentActionType;
import com.bloom.app.service.StockMovementService;
import com.bloom.app.domain.validation.InventoryQuantityValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockMovementServiceImpl implements StockMovementService {

    private final StockMovementRepository stockMovementRepository;
    private final ItemRepository itemRepository;
    private final ItemAuditLogRepository itemAuditLogRepository;

    /**
     * Generic method to record a stock movement.
     * Updates the Item stock quantity and logs to ItemAuditLog.
     */
    @Transactional
    public void recordMovement(
        MovementSourceType sourceType,
        Long sourceId,
        Item item,
        BigDecimal quantity,
        MovementType movementType,
        String referenceNo,
        StockLocation stockLocation
    ) {
        if (item == null) {
            throw new IllegalArgumentException("Item is required");
        }
        if (item.getId() == null) {
            throw new IllegalArgumentException("Item must be persisted before recording stock");
        }
        if (sourceType == null) {
            throw new IllegalArgumentException("Movement source type is required");
        }
        if (sourceId == null) {
            throw new IllegalArgumentException("Movement source id is required");
        }
        if (movementType == null) {
            throw new IllegalArgumentException("Movement type is required");
        }
        if (stockLocation == null) {
            throw new IllegalArgumentException("Stock location is required");
        }
        log.debug("Recording movement: item={}, qty={}, type={}, source={}, location={}", item.getSku(), quantity, movementType,
                sourceType, stockLocation);

        InventoryQuantityValidator.validateIncoming(quantity, item.isFractionalQuantityAllowed());

        BigDecimal previousStock = stockAt(item, stockLocation);
        BigDecimal signedEffect = movementType == MovementType.IN ? quantity : quantity.negate();
        BigDecimal newStock = previousStock.add(signedEffect);
        if (newStock.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientStockException(item.getSku(), stockLocation);
        }
        InventoryQuantityValidator.validateStock(newStock, item.isFractionalQuantityAllowed());

        // 1. Update Product Stock (Performance Optimization)
        if (stockLocation == StockLocation.STORE) {
            item.setStockStore(newStock);
        } else {
            item.setStockWarehouse(newStock);
        }
        Item persistedItem;
        try {
            persistedItem = itemRepository.saveAndFlush(item);
        } catch (OptimisticLockingFailureException exception) {
            throw new StockConcurrencyException(item.getSku(), exception);
        }

        // 2. Create StockMovement (Ledger)
        StockMovement movement = StockMovement.builder()
                .product(persistedItem)
                .movementType(movementType)
                .sourceType(sourceType)
                .sourceId(sourceId)
                .quantity(quantity)
                .stockLocation(stockLocation)
                .qtyBefore(previousStock)
                .qtyAfter(newStock)
                .build();
        stockMovementRepository.saveAndFlush(movement);

        ItemAuditLog auditLog = ItemAuditLog.builder()
                .item(persistedItem)
                .qty(quantity)
                .qtyBefore(previousStock)
                .qtyAfter(newStock)
                .source(sourceType)
                .referenceNo(referenceNo)
                .build();
        itemAuditLogRepository.saveAndFlush(auditLog);
    }

    // Helper to record sales
    @Transactional
    public void recordSaleMovements(Sale sale) {
        for (SaleItem saleItem : sale.getItems()) {
            recordMovement(
                    MovementSourceType.SALE,
                    sale.getId(),
                    saleItem.getItem(),
                    saleItem.getQuantity(),
                    MovementType.OUT,
                    sale.getCode(),
                    saleItem.getStockLocation()
            );
        }
    }

    // Helper for Manual Adjustments
    @Transactional
    public void recordManualAdjustment(StockAdjustment adjustment) {
        for (StockAdjustmentItem item : adjustment.getItems()) {
            MovementType type;
            BigDecimal qty = item.getChangeQuantity();

            // Logic to determine IN/OUT based on ActionType
            // ADD -> IN
            // REMOVE -> OUT
            // CORRECTION -> Calculated delta.
            // NOTE: The StockAdjustmentService already calculated delta logic before
            // calling this?
            // Or we move that logic here?
            // User said: "StockAdjustment will generate StockMovement entries"
            // The StockAdjustmentServiceImpl calculates 'changeQuantity' and 'newStock'.
            // We should use the calculated changeQuantity.

            if (item.getActionType() == StockAdjustmentActionType.ADD) {
                type = MovementType.IN;
            } else if (item.getActionType() == StockAdjustmentActionType.REMOVE) {
                type = MovementType.OUT;
            } else {
                // CORRECTION: Since we don't store 'delta' directly in StockAdjustmentItem
                // cleanly for IN/OUT
                // (it stores changeQuantity and newStock), we need to derive.
                // If previous < new, it's IN.
                // If previous > new, it's OUT.
                if (item.getNewStock().compareTo(item.getPreviousStock()) > 0) {
                    type = MovementType.IN;
                    qty = item.getNewStock().subtract(item.getPreviousStock());
                } else {
                    type = MovementType.OUT;
                    qty = item.getPreviousStock().subtract(item.getNewStock());
                }
            }

            if (qty.compareTo(BigDecimal.ZERO) > 0) { // Only record if there is a change
                recordMovement(
                    MovementSourceType.STOCK_ADJUSTMENT,
                    adjustment.getId(),
                    item.getItem(),
                    qty,
                    type,
                    adjustment.getStockAdjustmentCode(),
                    item.getStockLocation()
                );
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void validateBaseUnitOfMeasureChange(Item item, UnitOfMeasure requestedUnitOfMeasure) {
        if (item == null || requestedUnitOfMeasure == null
                || requestedUnitOfMeasure == item.getBaseUnitOfMeasure()) {
            return;
        }
        if (item.getId() != null && stockMovementRepository.existsByProductId(item.getId())) {
            throw new BaseUnitOfMeasureImmutableException(item.getSku());
        }
    }

    private BigDecimal stockAt(Item item, StockLocation stockLocation) {
        BigDecimal stock = switch (stockLocation) {
            case STORE -> item.getStockStore();
            case WAREHOUSE -> item.getStockWarehouse();
        };
        return stock == null ? BigDecimal.ZERO : stock;
    }
}
