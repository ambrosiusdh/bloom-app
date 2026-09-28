package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.Sale;
import com.bloom.app.persistence.projection.TopCategoryProjection;
import com.bloom.app.persistence.projection.DashboardSalesTodayTotals;
import com.bloom.app.persistence.projection.DashboardSalesDayTotals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    @Query(value = """
        WITH daily AS (
            SELECT day::date AS business_date,
                   COALESCE(SUM(sale.total_amount), 0) AS sales_amount,
                   COUNT(sale.id) AS transaction_count
            FROM generate_series(
                CAST(:periodStartDate AS date),
                CAST(:periodEndDate AS date),
                INTERVAL '1 day'
            ) AS day
            LEFT JOIN sales sale
              ON sale.created_at >= (day::date::timestamp AT TIME ZONE :storeZoneId)
             AND sale.created_at < ((day::date + 1)::timestamp AT TIME ZONE :storeZoneId)
            GROUP BY day::date
        )
        SELECT business_date AS "businessDate",
               sales_amount AS "salesAmount",
               transaction_count AS "transactionCount",
               SUM(sales_amount) OVER () AS "periodSalesAmount",
               SUM(transaction_count) OVER () AS "periodTransactionCount"
        FROM daily
        ORDER BY business_date
        """, nativeQuery = true)
    List<DashboardSalesDayTotals> summarizeOperationalSalesLast7Days(
        @Param("periodStartDate") LocalDate periodStartDate,
        @Param("periodEndDate") LocalDate periodEndDate,
        @Param("storeZoneId") String storeZoneId);

    @Query(value = """
        SELECT COALESCE(SUM(sale.total_amount), 0) AS "salesAmount",
               COUNT(*) AS "transactionCount"
        FROM sales sale
        WHERE sale.created_at >= :periodStart
          AND sale.created_at < :periodEndExclusive
        """, nativeQuery = true)
    DashboardSalesTodayTotals summarizeOperationalSales(
        @Param("periodStart") Instant periodStart,
        @Param("periodEndExclusive") Instant periodEndExclusive);

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

    @EntityGraph(attributePaths = {"cashSession", "items", "items.item"})
    @Query("SELECT s FROM Sale s WHERE s.code = :code")
    Optional<Sale> findReadModelByCode(@Param("code") String code);

    @EntityGraph(attributePaths = {"cashSession", "items", "items.item"})
    @Query("SELECT DISTINCT s FROM Sale s WHERE s.id IN :ids")
    List<Sale> findReadModelsByIdIn(@Param("ids") Collection<Long> ids);

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
