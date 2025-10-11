package com.bloom.app.repository;

import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long>, JpaSpecificationExecutor<Item> {
    Optional<Item> findItemBySku(String sku);

    List<Item> findAllByCategory(ItemCategory category);

    boolean existsBySku(String sku);

    long countByCategoryAndActiveTrue(ItemCategory category);

    long countByCategory(ItemCategory category);
}
