package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.stocktransfer.FilterStockTransferRequest;
import com.bloom.app.domain.model.StockTransfer;
import com.bloom.app.domain.model.StockTransferLine;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StockTransferSpecification {
    private StockTransferSpecification() {
    }

    public static Specification<StockTransfer> filter(FilterStockTransferRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            String normalizedCode = normalizeCode(request.getCode());
            if (normalizedCode != null) {
                predicates.add(criteriaBuilder.equal(root.get("code"), normalizedCode));
            }
            if (request.getSourceLocation() != null) {
                predicates.add(criteriaBuilder.equal(
                    root.get("sourceLocation"), request.getSourceLocation()));
            }
            if (request.getDestinationLocation() != null) {
                predicates.add(criteriaBuilder.equal(
                    root.get("destinationLocation"), request.getDestinationLocation()));
            }
            if (request.getCreatedFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("createdAt"), request.getCreatedFrom()));
            }
            if (request.getCreatedTo() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                    root.get("createdAt"), request.getCreatedTo()));
            }
            if (request.getItemId() != null) {
                Subquery<Long> itemExists = query.subquery(Long.class);
                Root<StockTransferLine> line = itemExists.from(StockTransferLine.class);
                itemExists.select(criteriaBuilder.literal(1L));
                itemExists.where(
                    criteriaBuilder.equal(line.get("stockTransfer").get("id"), root.get("id")),
                    criteriaBuilder.equal(line.get("item").get("id"), request.getItemId())
                );
                predicates.add(criteriaBuilder.exists(itemExists));
            }

            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }
}
