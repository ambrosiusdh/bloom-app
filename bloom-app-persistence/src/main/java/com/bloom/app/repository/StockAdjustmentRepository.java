package com.bloom.app.repository;

import com.bloom.app.domain.model.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface StockAdjustmentRepository
        extends JpaRepository<StockAdjustment, Long>, JpaSpecificationExecutor<StockAdjustment> {
    Optional<StockAdjustment> findByStockAdjustmentCode(String stockAdjustmentCode);

    long countByCreatedAtBetween(Instant start, Instant end);
}
