package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.StockAdjustmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockAdjustmentItemRepository extends JpaRepository<StockAdjustmentItem, Long> {
}
