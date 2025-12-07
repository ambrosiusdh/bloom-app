package com.bloom.app.repository;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.MovementType;
import com.bloom.app.domain.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
    List<StockMovement> findByProductId(Long productId);

    List<StockMovement> findByCreatedAtBetween(Instant startDate, Instant endDate);

    List<StockMovement> findBySourceTypeAndSourceId(MovementSourceType sourceType, Long sourceId);
}
