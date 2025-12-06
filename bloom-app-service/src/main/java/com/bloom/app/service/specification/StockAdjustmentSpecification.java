package com.bloom.app.service.specification;

import com.bloom.app.domain.dto.request.stockadjustment.FilterStockAdjustmentRequest;
import com.bloom.app.domain.model.StockAdjustment;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class StockAdjustmentSpecification {
    public static Specification<StockAdjustment> filter(FilterStockAdjustmentRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getStockAdjustmentCode() != null && !request.getStockAdjustmentCode().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("stockAdjustmentCode")),
                        "%" + request.getStockAdjustmentCode().toLowerCase() + "%"));
            }

            if (request.getSource() != null) {
                predicates.add(criteriaBuilder.equal(root.get("source"), request.getSource()));
            }

            if (request.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.getStartDate()));
            }

            if (request.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.getEndDate()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
