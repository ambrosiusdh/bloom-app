package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.persistence.projection.DashboardStockAttentionPreview;
import com.bloom.app.persistence.projection.DashboardStockAttentionTotals;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {
    @Query(value = """
        SELECT COUNT(*) FILTER (WHERE item.stock_store <= 0) AS "outOfStockCount",
               COUNT(*) FILTER (
                   WHERE item.stock_store > 0 AND item.stock_store < :threshold
               ) AS "lowStockCount"
        FROM items item
        WHERE item.active = TRUE
        """, nativeQuery = true)
    DashboardStockAttentionTotals summarizeDashboardStockAttention(
        @Param("threshold") BigDecimal threshold);

    @Query(value = """
        SELECT item.id AS "itemId",
               item.sku AS "sku",
               item.name AS "name",
               item.base_unit_of_measure AS "baseUnitOfMeasure",
               item.stock_store AS "stockStore",
               CASE
                   WHEN item.stock_store <= 0 THEN 'OUT_OF_STOCK'
                   ELSE 'LOW_STOCK'
               END AS "state"
        FROM items item
        WHERE item.active = TRUE
          AND (
              item.stock_store <= 0
              OR (item.stock_store > 0 AND item.stock_store < :threshold)
          )
        ORDER BY CASE WHEN item.stock_store <= 0 THEN 0 ELSE 1 END,
                 item.stock_store ASC,
                 item.id ASC
        LIMIT 3
        """, nativeQuery = true)
    List<DashboardStockAttentionPreview> findDashboardStockAttentionPreview(
        @Param("threshold") BigDecimal threshold);

    Optional<Item> findItemBySku(String sku);

    List<Item> findBySkuIn(List<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.sku IN :skus ORDER BY i.id")
    List<Item> findBySkuInOrderByIdForUpdate(@Param("skus") List<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id IN :ids ORDER BY i.id")
    List<Item> findByIdInOrderByIdForUpdate(@Param("ids") List<Long> ids);

    // TODO: improve this query, either total stock need to be recorded or else
    @Query("SELECT i FROM Item i WHERE (i.stockStore + i.stockWarehouse) < :quantity")
    List<Item> findByStockQuantityLessThan(@Param("quantity") BigDecimal quantity);

    List<Item> findAllByCategory(ItemCategory category);

    List<Item> findAllByCategoryAndActiveTrueOrderBySkuAsc(ItemCategory category);

    boolean existsBySku(String sku);

    long countByCategoryAndActiveTrue(ItemCategory category);

    long countByCategory(ItemCategory category);
}
