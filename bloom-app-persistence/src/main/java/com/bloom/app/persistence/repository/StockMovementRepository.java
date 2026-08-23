package com.bloom.app.persistence.repository;

import com.bloom.app.domain.enums.MovementSourceType;
import com.bloom.app.domain.enums.StockLocation;
import com.bloom.app.domain.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

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

    @Query("SELECT DISTINCT movement.product.id FROM StockMovement movement "
        + "WHERE movement.product.id IN :itemIds")
    Set<Long> findProductIdsWithMovements(@Param("itemIds") Collection<Long> itemIds);
}
