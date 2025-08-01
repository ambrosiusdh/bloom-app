package com.bloom.app.service.specification;

import com.bloom.app.domain.dto.request.itemcategory.FilterItemCategoryRequest;
import com.bloom.app.domain.model.ItemCategory;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ItemCategorySpecification {
    public static Specification<ItemCategory> filter(FilterItemCategoryRequest request) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));

            if (request.getCode() != null && !request.getCode().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("code")), "%" + request.getCode().toLowerCase() + "%"));
            }

            if (request.getName() != null && !request.getName().isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + request.getName().toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
