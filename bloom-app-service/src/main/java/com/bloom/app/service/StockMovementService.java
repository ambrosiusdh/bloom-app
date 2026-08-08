package com.bloom.app.service;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.Sale;
import com.bloom.app.domain.model.StockAdjustment;
import com.bloom.app.domain.model.UnitOfMeasure;

import java.math.BigDecimal;

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
    void validateBaseUnitOfMeasureChange(Item item, UnitOfMeasure requestedUnitOfMeasure);
}
