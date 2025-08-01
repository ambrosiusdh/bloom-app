package com.bloom.app.service.specification;

import com.bloom.app.domain.dto.request.item.FilterItemRequest;
import com.bloom.app.domain.model.Item;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ItemSpecification {
    public static Specification<Item> filter(FilterItemRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.isTrue(root.get("active")));

            if (request.getName() != null && !request.getName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + request.getName().toLowerCase() + "%"));
            }

            if (request.getSku() != null && !request.getSku().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("sku")), "%" + request.getSku().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
