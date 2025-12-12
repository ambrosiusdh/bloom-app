package com.bloom.app.service.specification;

import com.bloom.app.api.dto.request.auditlog.FilterAuditLogRequest;
import com.bloom.app.domain.model.ItemAuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ItemAuditLogSpecification {
    public static Specification<ItemAuditLog> filter(FilterAuditLogRequest request) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (request.getItemSku() != null && !request.getItemSku().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("item").get("sku"), request.getItemSku()));
            }

            if (request.getActionType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("actionType"), request.getActionType()));
            }

            if (request.getReferenceNo() != null && !request.getReferenceNo().isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("referenceNo")),
                        "%" + request.getReferenceNo().toLowerCase() + "%"));
            }

            if (request.getStartDate() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("createdDate"), request.getStartDate()));
            }

            if (request.getEndDate() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("createdDate"), request.getEndDate()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
