package com.bloom.app.repository;

import com.bloom.app.domain.model.ItemCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory,Long>, JpaSpecificationExecutor<ItemCategory> {
    boolean existsByCode(String code);
    Optional<ItemCategory> findByCode(String code);
}
