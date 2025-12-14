package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.goodsreceipt.FilterGoodsReceiptRequest;
import com.bloom.app.domain.model.GoodsReceipt;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class GoodsReceiptSpecification {
    public static Specification<GoodsReceipt> filter(FilterGoodsReceiptRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getCode() != null && !request.getCode().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("code")),
                        "%" + request.getCode().toLowerCase() + "%"));
            }

            if (request.getSupplierName() != null && !request.getSupplierName().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("supplierName")),
                        "%" + request.getSupplierName().toLowerCase() + "%"));
            }

            if (request.getReceivedDateFrom() != null) {
                predicates.add(
                        criteriaBuilder.greaterThanOrEqualTo(root.get("receivedDate"), request.getReceivedDateFrom()));
            }

            if (request.getReceivedDateTo() != null) {
                predicates
                        .add(criteriaBuilder.lessThanOrEqualTo(root.get("receivedDate"), request.getReceivedDateTo()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
