package com.bloom.app.persistence.repository;

import com.bloom.app.domain.model.StockTransfer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    @Query(
        value = "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:requestKey AS text), 0))",
        nativeQuery = true
    )
    void lockRequestKey(@Param("requestKey") String requestKey);

    @EntityGraph(attributePaths = {"lines", "lines.item", "lines.item.category"})
    Optional<StockTransfer> findByRequestKey(String requestKey);

    @EntityGraph(attributePaths = {"lines", "lines.item", "lines.item.category"})
    Optional<StockTransfer> findByCode(String code);
}
