package com.bloom.app.persistence.repository;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface StockMovementRepository
        extends JpaRepository<StockMovement, Long>, JpaSpecificationExecutor<StockMovement> {
    @Override
    @EntityGraph(attributePaths = {"product", "product.category"})
    Page<StockMovement> findAll(Specification<StockMovement> specification, Pageable pageable);

    List<StockMovement> findByProductId(Long productId);

    List<StockMovement> findByCreatedAtBetween(Instant startDate, Instant endDate);

    List<StockMovement> findBySourceTypeAndSourceId(MovementSourceType sourceType, Long sourceId);

    boolean existsBySourceTypeAndSourceIdAndProduct_IdAndStockLocation(
        MovementSourceType sourceType,
        Long sourceId,
        Long productId,
        StockLocation stockLocation
    );

    boolean existsByProductId(Long productId);
}
