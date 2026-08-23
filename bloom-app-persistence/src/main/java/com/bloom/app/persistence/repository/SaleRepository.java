package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.Sale;
import com.bloom.app.persistence.projection.TopCategoryProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:checkoutKey AS text), 0))",
        nativeQuery = true
    )
    void lockCheckoutKey(@Param("checkoutKey") String checkoutKey);

    @EntityGraph(attributePaths = {"cashSession", "items", "items.item"})
    Optional<Sale> findByCheckoutIdempotencyKey(String checkoutIdempotencyKey);

    long countByCreatedAtBetween(Instant startDate, Instant endDate);

    List<Sale> findByCreatedAtBetween(Instant startDate, Instant endDate);

    Optional<Sale> findByCode(String code);

    @Query("SELECT c.name as name, SUM(si.subtotal) as total " +
            "FROM Sale s " +
            "JOIN s.items si " +
            "JOIN si.item i " +
            "JOIN i.category c " +
            "GROUP BY c.name " +
            "ORDER BY SUM(si.subtotal) DESC")
    List<TopCategoryProjection> findTopCategories(Pageable pageable);

    @Query("SELECT SUM(si.subtotal) FROM Sale s JOIN s.items si")
    BigDecimal getTotalRevenue();
}
