package com.bloom.app.validation.validator;

import com.bloom.app.repository.ItemRepository;
import com.bloom.app.validation.UniqueItemSku;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class UniqueItemSkuValidator implements ConstraintValidator<UniqueItemSku, String> {
    private final ItemRepository itemRepository;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext constraintValidatorContext) {
        if (value == null || value.isBlank()) {
            return true;
        }

        return itemRepository.existsBySku(value);
    }
}
