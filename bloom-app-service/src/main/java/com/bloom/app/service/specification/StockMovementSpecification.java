package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.stockmovement.FilterStockMovementRequest;
import com.bloom.app.domain.model.Item;
import com.bloom.app.domain.model.StockMovement;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StockMovementSpecification {
    private StockMovementSpecification() {
    }

    public static Specification<StockMovement> filter(FilterStockMovementRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            Join<StockMovement, Item> product = null;

            if (request.getItemId() != null || hasText(request.getItemSku())) {
                product = root.join("product");
            }
            if (request.getItemId() != null) {
                predicates.add(criteriaBuilder.equal(product.get("id"), request.getItemId()));
            }
            if (hasText(request.getItemSku())) {
                predicates.add(criteriaBuilder.equal(
                    criteriaBuilder.lower(product.get("sku")), normalize(request.getItemSku())));
            }
            if (request.getSourceType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("sourceType"), request.getSourceType()));
            }
            if (request.getMovementType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("movementType"), request.getMovementType()));
            }
            if (request.getAdjustmentActionType() != null) {
                predicates.add(criteriaBuilder.equal(
                    root.get("adjustmentActionType"), request.getAdjustmentActionType()));
            }
            if (request.getLocation() != null) {
                predicates.add(criteriaBuilder.equal(root.get("stockLocation"), request.getLocation()));
            }
            if (request.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), request.getStartDate()));
            }
            if (request.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), request.getEndDate()));
            }
            if (hasText(request.getReference())) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("referenceNo")),
                    "%" + escapeLike(normalize(request.getReference())) + "%",
                    '\\'));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
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
