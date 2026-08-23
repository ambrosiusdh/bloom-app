package com.bloom.app.service;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.UnitOfMeasure;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Set;

public interface StockMovementService {
    void recordMovement(
        MovementSourceType sourceType,
        Long sourceId,
        Item item,
        BigDecimal quantity,
        MovementType movementType,
        String referenceNo,
        StockLocation stockLocation
    );
    void recordSaleMovements(Sale sale);
    void recordManualAdjustment(StockAdjustment adjustment);
    boolean validateMeasurementRuleChanges(
        Item item,
        UnitOfMeasure requestedUnitOfMeasure,
        Boolean requestedFractionalQuantityAllowed
    );
    boolean hasStockMovements(Long itemId);
    Set<Long> findItemIdsWithStockMovements(Collection<Long> itemIds);
}
