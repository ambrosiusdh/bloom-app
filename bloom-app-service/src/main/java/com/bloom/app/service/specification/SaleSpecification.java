package com.bloom.app.service.specification;

import com.bloom.app.domain.dto.request.sale.FilterSaleRequest;
import com.bloom.app.domain.model.Sale;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class SaleSpecification {
    public static Specification<Sale> filter(FilterSaleRequest request) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getCode() != null && !request.getCode().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("code")), "%" + request.getCode().toLowerCase() + "%"));
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
