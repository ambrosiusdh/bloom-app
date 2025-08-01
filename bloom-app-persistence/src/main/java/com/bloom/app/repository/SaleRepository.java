package com.bloom.app.repository;

import com.bloom.app.domain.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    long countByCreatedAtBetween(Instant startDate, Instant endDate);

    Optional<Sale> findByCode(String code);
}
