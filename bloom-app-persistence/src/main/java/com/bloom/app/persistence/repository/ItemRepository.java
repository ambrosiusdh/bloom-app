package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {
    Optional<Item> findItemBySku(String sku);

    List<Item> findBySkuIn(List<String> skus);

    // TODO: improve this query, either total stock need to be recorded or else
    @Query("SELECT i FROM Item i WHERE (i.stockStore + i.stockWarehouse) < :quantity")
    List<Item> findByStockQuantityLessThan(@Param("quantity") Integer quantity);

    List<Item> findAllByCategory(ItemCategory category);

    boolean existsBySku(String sku);

    long countByCategoryAndActiveTrue(ItemCategory category);

    long countByCategory(ItemCategory category);
}
