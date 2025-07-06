package com.bloom.app.repository;

import com.bloom.app.domain.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, Long> {
    Optional<Item> findItemBySku(String sku);
}
