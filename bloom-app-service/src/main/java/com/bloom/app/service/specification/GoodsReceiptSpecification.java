package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.domain.model.GoodsReceipt;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class GoodsReceiptSpecification {
    public static Specification<GoodsReceipt> filter(
            FilterGoodsReceiptRequest request, ZoneId storeZone) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getCode() != null && !request.getCode().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("code")),
                        "%" + request.getCode().toLowerCase() + "%"));
            }

            if (request.getSupplierName() != null && !request.getSupplierName().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("supplier").get("name")),
                        "%" + request.getSupplierName().toLowerCase() + "%"));
            }

            if (request.getReceivedDateFrom() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                    root.get("receivedDate"), startInclusive(request.getReceivedDateFrom(), storeZone)));
            }

            if (request.getReceivedDateTo() != null) {
                predicates.add(criteriaBuilder.lessThan(
                    root.get("receivedDate"), endExclusive(request.getReceivedDateTo(), storeZone)));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    static Instant startInclusive(LocalDate date, ZoneId storeZone) {
        return date.atStartOfDay(storeZone).toInstant();
    }

    static Instant endExclusive(LocalDate date, ZoneId storeZone) {
        return date.plusDays(1).atStartOfDay(storeZone).toInstant();
    }
}
