package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
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
    Optional<Item> findItemBySku(String sku);

    List<Item> findBySkuIn(List<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.sku IN :skus ORDER BY i.id")
    List<Item> findBySkuInOrderByIdForUpdate(@Param("skus") List<String> skus);

    // TODO: improve this query, either total stock need to be recorded or else
    @Query("SELECT i FROM Item i WHERE (i.stockStore + i.stockWarehouse) < :quantity")
    List<Item> findByStockQuantityLessThan(@Param("quantity") BigDecimal quantity);

    List<Item> findAllByCategory(ItemCategory category);

    boolean existsBySku(String sku);

    long countByCategoryAndActiveTrue(ItemCategory category);

    long countByCategory(ItemCategory category);
}
