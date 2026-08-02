package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.ItemCategoryCounter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ItemCategoryCounterRepository extends JpaRepository<ItemCategoryCounter, Long> {
    Optional<ItemCategoryCounter> findByItemCategory(ItemCategory itemCategory);
}
