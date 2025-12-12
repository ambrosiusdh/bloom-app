package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.GoodsReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface GoodsReceiptRepository extends JpaRepository<GoodsReceipt, Long> {
    Optional<GoodsReceipt> findByCode(String code);

    @Query("SELECT COUNT(g) FROM GoodsReceipt g WHERE g.createdAt BETWEEN :start AND :end")
    long countByCreatedAtBetween(Instant start, Instant end);
}
