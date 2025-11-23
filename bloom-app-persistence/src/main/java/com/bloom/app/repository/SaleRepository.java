package com.bloom.app.repository;

import com.bloom.app.domain.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.bloom.app.domain.dto.response.dashboard.CategoryDto;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long>, JpaSpecificationExecutor<Sale> {
    long countByCreatedAtBetween(Instant startDate, Instant endDate);

    List<Sale> findByCreatedAtBetween(Instant startDate, Instant endDate);

    Optional<Sale> findByCode(String code);

    @Query("SELECT new com.bloom.app.domain.dto.response.dashboard.CategoryDto(c.name, SUM(si.subtotal)) " +
            "FROM Sale s " +
            "JOIN s.items si " +
            "JOIN si.item i " +
            "JOIN i.category c " +
            "GROUP BY c.name " +
            "ORDER BY SUM(si.subtotal) DESC")
    List<CategoryDto> findTopCategories(Pageable pageable);

    @Query("SELECT SUM(si.subtotal) FROM Sale s JOIN s.items si")
    BigDecimal getTotalRevenue();
}
