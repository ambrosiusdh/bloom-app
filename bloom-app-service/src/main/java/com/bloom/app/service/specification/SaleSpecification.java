package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.sale.FilterSaleRequest;
import com.bloom.app.domain.model.Sale;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SaleSpecification {
    public static Specification<Sale> filter(FilterSaleRequest request) {
        return (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hasText(request.getCode())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("code")),
                    "%" + escapeLike(normalize(request.getCode())) + "%",
                    '\\'));
            }

            if (hasText(request.getCreatedBy())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("createdBy")),
                    "%" + escapeLike(normalize(request.getCreatedBy())) + "%",
                    '\\'));
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
