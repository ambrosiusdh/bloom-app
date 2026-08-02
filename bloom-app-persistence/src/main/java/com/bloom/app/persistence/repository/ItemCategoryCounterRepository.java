package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.ItemCategory;
import com.bloom.app.domain.model.ItemCategoryCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface ItemCategoryCounterRepository extends JpaRepository<ItemCategoryCounter, Long> {
    Optional<ItemCategoryCounter> findByItemCategory(ItemCategory itemCategory);

    @Transactional
    @Query(value = """
        INSERT INTO item_category_counters (item_category_id, current_sequence)
        VALUES (:itemCategoryId, 1)
        ON CONFLICT (item_category_id)
        DO UPDATE SET current_sequence = item_category_counters.current_sequence + 1
        RETURNING current_sequence
        """, nativeQuery = true)
    long incrementAndGetSequence(@Param("itemCategoryId") Long itemCategoryId);
}
